#!/usr/bin/env bash
# Walks through phase 1 from the outside, the way a client would, through the gateway.
#
#   scripts/demo.sh                              gateway on http://localhost:8080, token checks off
#   TOKEN=eyJ... scripts/demo.sh                 the same, with a Keycloak token
#   GATEWAY=http://localhost:8080 scripts/demo.sh
#
# Start the gateway, ledger, payments and notification services first. It needs only bash and curl.
# Every step prints what it expected next to what it got, and the script ends with a count.
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
CASH="00000000-0000-0000-0000-000000000100"

passed=0
failed=0
status=""
body=""
waited=""

new_key() {
  if command -v uuidgen >/dev/null 2>&1; then uuidgen | tr '[:upper:]' '[:lower:]'; else cat /proc/sys/kernel/random/uuid; fi
}

# call METHOD PATH [JSON] [IDEMPOTENCY-KEY]   sets $status and $body
call() {
  local method="$1" path="$2" json="${3:-}" key="${4:-$(new_key)}"
  local args=(-sS --max-time 20 -X "$method" -w $'\n%{http_code}' -H "Accept: application/json")
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")
  if [ "$method" != GET ]; then
    args+=(-H "Idempotency-Key: $key")
  fi
  if [ -n "$json" ]; then
    args+=(-H "Content-Type: application/json" -d "$json")
  fi
  local out
  for _ in $(seq 1 40); do
    out="$(curl "${args[@]}" "$GATEWAY$path" 2>&1)"
    status="${out##*$'\n'}"
    body="${out%$'\n'*}"
    # The gateway allows each client 120 requests a minute. Past that it answers 429, so wait and go on.
    [ "$status" != 429 ] && return 0
    [ -z "${waited:-}" ] && echo "  (the gateway's rate limit was reached, waiting for it to refill)" && waited=yes
    sleep 3
  done
}

# field NAME   the first "NAME":"value" or "NAME":number in $body
field() {
  printf '%s' "$body" | grep -o "\"$1\":\"[^\"]*\"\|\"$1\":[0-9.truefalsn]*" | head -1 | sed -e "s/^\"$1\"://" -e 's/"//g'
}

# amount_of NAME   the amount inside "NAME":{"amount":"12.34",...}
amount_of() {
  printf '%s' "$body" | grep -o "\"$1\":{[^}]*}" | head -1 | grep -o '"amount":"[^"]*"' | sed -e 's/"amount"://' -e 's/"//g'
}

expect() {
  local what="$1" want="$2" got="$3"
  if [ "$want" = "$got" ]; then
    passed=$((passed + 1))
    printf '  ok    %-58s %s\n' "$what" "$got"
  else
    failed=$((failed + 1))
    printf '  FAIL  %-58s wanted %s, got %s\n' "$what" "$want" "$got"
    printf '        %s\n' "$body" | cut -c1-400
  fi
}

step() { printf '\n%s\n' "$1"; }

balance() { call GET "/ledger/v1/accounts/$1/balance"; }

# wait_status PAYMENT-ID STATUS   the saga finishes some payments in the background
wait_status() {
  for _ in $(seq 1 40); do
    call GET "/payments/v1/payments/$1"
    [ "$(field status)" = "$2" ] && return 0
    sleep 0.5
  done
  return 1
}

payment_json() {
  local debtor="$1" rail="$2" name="$3" account="$4" amount="$5" routing=""
  [ "$rail" != BOOK ] && routing=',"routingNumber":"021000021"'
  printf '{"debtorAccountId":"%s","creditor":{"name":"%s","accountNumber":"%s"%s},"amount":{"amount":"%s","currency":"USD"},"rail":"%s","endToEndId":"DEMO-1","remittanceInformation":"Demo payment"}' \
    "$debtor" "$name" "$account" "$routing" "$amount" "$rail"
}

echo "Gateway $GATEWAY"
call GET "/ledger/v1/info"
if [ "$status" != 200 ]; then
  echo "The gateway or the ledger service is not answering (HTTP $status). Start them first."
  [ "$status" = 401 ] && echo "The gateway wants a token. Pass one with TOKEN=... or start it with GATEWAY_SECURITY=false."
  exit 1
fi

step "1. Open two accounts"
alice_key="$(new_key)"
call POST "/ledger/v1/accounts" '{"customerId":"11111111-1111-1111-1111-111111111111","type":"CHECKING","currency":"USD"}' "$alice_key"
expect "Alice's account is created" 201 "$status"
alice="$(field id)"
call POST "/ledger/v1/accounts" '{"customerId":"11111111-1111-1111-1111-111111111111","type":"CHECKING","currency":"USD"}' "$alice_key"
expect "The same request again returns the same account" "$alice" "$(field id)"
call POST "/ledger/v1/accounts" '{"customerId":"22222222-2222-2222-2222-222222222222","type":"CHECKING","currency":"USD"}'
expect "Bob's account is created" 201 "$status"
bob="$(field id)"

step "2. Pay 500.00 into Alice's account (a journal entry, cash against her account)"
call POST "/ledger/v1/journal-entries" \
  "{\"reference\":\"demo-deposit-$alice\",\"postings\":[{\"accountId\":\"$CASH\",\"side\":\"DEBIT\",\"amount\":{\"amount\":\"500.00\",\"currency\":\"USD\"}},{\"accountId\":\"$alice\",\"side\":\"CREDIT\",\"amount\":{\"amount\":\"500.00\",\"currency\":\"USD\"}}]}"
expect "The entry is posted" 201 "$status"
balance "$alice"
expect "Alice has 500.00" 500.00 "$(amount_of ledger)"

step "3. An entry whose two sides differ is refused"
call POST "/ledger/v1/journal-entries" \
  "{\"reference\":\"demo-bad-$alice\",\"postings\":[{\"accountId\":\"$CASH\",\"side\":\"DEBIT\",\"amount\":{\"amount\":\"10.00\",\"currency\":\"USD\"}},{\"accountId\":\"$alice\",\"side\":\"CREDIT\",\"amount\":{\"amount\":\"9.00\",\"currency\":\"USD\"}}]}"
expect "Refused with 422" 422 "$status"
expect "The reason is given as a code" UNBALANCED_ENTRY "$(field code)"

step "4. A book payment, Alice to Bob, inside the bank"
pay_key="$(new_key)"
call POST "/payments/v1/payments" "$(payment_json "$alice" BOOK "Bob" "$bob" 120.00)" "$pay_key"
expect "Accepted" 202 "$status"
book="$(field id)"
wait_status "$book" ACSC
expect "Settled (ISO 20022 status ACSC)" ACSC "$(field status)"
balance "$alice"
expect "Alice has 380.00" 380.00 "$(amount_of ledger)"
balance "$bob"
expect "Bob has 120.00" 120.00 "$(amount_of ledger)"

step "5. The same payment request again, same Idempotency-Key"
call POST "/payments/v1/payments" "$(payment_json "$alice" BOOK "Bob" "$bob" 120.00)" "$pay_key"
expect "The first payment is returned, no second one is made" "$book" "$(field id)"
balance "$alice"
expect "Alice still has 380.00" 380.00 "$(amount_of ledger)"

step "6. The same key with a different amount is refused"
call POST "/payments/v1/payments" "$(payment_json "$alice" BOOK "Bob" "$bob" 999.00)" "$pay_key"
expect "Refused with 422" 422 "$status"

step "7. An instant payment to another bank"
call POST "/payments/v1/payments" "$(payment_json "$alice" INSTANT "Jane Doe" 123456789 75.25)"
instant="$(field id)"
wait_status "$instant" ACSC
expect "Settled" ACSC "$(field status)"
call GET "/payments/v1/payments/$instant/messages"
expect "A pacs.008 went out" pacs.008.001.08 "$(field type)"
case "$body" in *pacs.002.001.10*) got=yes ;; *) got=no ;; esac
expect "A pacs.002 answer came back" yes "$got"
balance "$alice"
expect "Alice has 304.75" 304.75 "$(amount_of ledger)"

step "8. An instant payment the other bank refuses (their account is closed)"
call POST "/payments/v1/payments" "$(payment_json "$alice" INSTANT "Jane Doe" 99990000 40.00)"
refused="$(field id)"
wait_status "$refused" RJCT
expect "Rejected" RJCT "$(field status)"
expect "Reason AC04, closed account" AC04 "$(field reasonCode)"
# The undo step gives the money back in the background, so look a few times.
for _ in $(seq 1 20); do
  balance "$alice"
  [ "$(amount_of available)" = 304.75 ] && break
  sleep 0.5
done
expect "The reserved money is back, 304.75 available" 304.75 "$(amount_of available)"

step "9. A payment for more than Alice has"
call POST "/payments/v1/payments" "$(payment_json "$alice" BOOK "Bob" "$bob" 9000.00)"
expect "Rejected" RJCT "$(field status)"
expect "Reason AM04, not enough money" AM04 "$(field reasonCode)"

step "10. An ACH payment, which settles later in a batch"
call POST "/payments/v1/payments" "$(payment_json "$alice" ACH "Utility Co" 5550001234 50.00)"
ach="$(field id)"
wait_status "$ach" ACSP
expect "Accepted and waiting for settlement (ACSP)" ACSP "$(field status)"
balance "$alice"
expect "Balance unchanged at 304.75" 304.75 "$(amount_of ledger)"
expect "But only 254.75 can be spent, 50.00 is held" 254.75 "$(amount_of available)"

step "11. A payment that is already on its way cannot be cancelled"
call POST "/payments/v1/payments/$ach/cancel"
expect "Refused with 409" 409 "$status"
expect "The reason is given as a code" PAYMENT_STATE_CONFLICT "$(field code)"

step "12. Notifications, built from the events the other services published"
# They arrive through Kafka a moment after the payments, so look a few times.
sent=no
refused=no
for _ in $(seq 1 40); do
  call GET "/notifications/v1/notifications?accountId=$alice"
  case "$body" in *"120.00 USD to Bob has been sent"*) sent=yes ;; esac
  case "$body" in *"not enough money"*) refused=yes ;; esac
  [ "$sent" = yes ] && [ "$refused" = yes ] && break
  sleep 0.5
done
expect "Alice was told her payment to Bob was sent" yes "$sent"
expect "And why the large one was not" yes "$refused"

step "13. A request with no Idempotency-Key is refused"
status="$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  ${TOKEN:+-H "Authorization: Bearer $TOKEN"} -d "$(payment_json "$alice" BOOK "Bob" "$bob" 1.00)" "$GATEWAY/payments/v1/payments")"
body=""
expect "Refused with 400" 400 "$status"

printf '\n%d checks passed, %d failed\n' "$passed" "$failed"
[ "$failed" -eq 0 ]
