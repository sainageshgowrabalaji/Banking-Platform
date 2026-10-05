#!/usr/bin/env bash
# Prints an access token from the local Keycloak, for one of the two laptop users.
#
#   scripts/token.sh            alice, a customer
#   scripts/token.sh olivia     an operator, who may also post journal entries
#
#   TOKEN=$(scripts/token.sh olivia) scripts/demo.sh
#
# Keycloak comes from infra/docker-compose.yml. The users and their passwords are in
# infra/keycloak/bank-realm.json and are for a laptop only.
set -euo pipefail

user="${1:-alice}"
keycloak="${KEYCLOAK_URL:-http://localhost:8090}"

response="$(curl -sS --max-time 10 \
  -d grant_type=password \
  -d client_id=bank-cli \
  -d "username=$user" \
  -d "password=${user}_local_only" \
  "$keycloak/realms/bank/protocol/openid-connect/token")" || {
  echo "Keycloak is not answering at $keycloak. Start it with: docker compose -f infra/docker-compose.yml up -d" >&2
  exit 1
}

token="$(printf '%s' "$response" | grep -o '"access_token":"[^"]*"' | sed -e 's/"access_token":"//' -e 's/"$//')"
if [ -z "$token" ]; then
  echo "Keycloak did not give a token for $user. It said: $response" >&2
  exit 1
fi
printf '%s\n' "$token"
