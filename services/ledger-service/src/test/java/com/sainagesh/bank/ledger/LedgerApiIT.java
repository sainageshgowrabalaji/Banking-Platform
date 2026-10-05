package com.sainagesh.bank.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.testing.HttpJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ledger API from the outside, over HTTP, against a real database. */
class LedgerApiIT extends LedgerIntegrationTest {

    @Test
    void anAccountIsOpenedAndRead() {
        String customer = UUID.randomUUID().toString();

        HttpJson.Response opened = api.post(
                "/v1/accounts", Map.of("customerId", customer, "type", "SAVINGS", "currency", "USD", "name", "Rainy day"));

        assertEquals(201, opened.status());
        assertEquals("/v1/accounts/" + opened.text("/id"), opened.header("Location"));
        assertEquals("ACTIVE", opened.text("/status"));

        HttpJson.Response read = api.get("/v1/accounts/" + opened.text("/id"));
        assertEquals(200, read.status());
        assertEquals(customer, read.text("/customerId"));
        assertEquals("SAVINGS", read.text("/type"));
        assertEquals("Rainy day", read.text("/name"));
        assertEquals("0.00", ledgerBalance(opened.text("/id")));
    }

    @Test
    void aRetryWithTheSameIdempotencyKeyOpensOneAccount() {
        Map<String, Object> request =
                Map.of("customerId", UUID.randomUUID().toString(), "type", "CHECKING", "currency", "USD");
        String key = UUID.randomUUID().toString();

        HttpJson.Response first = api.post("/v1/accounts", request, key);
        HttpJson.Response retry = api.post("/v1/accounts", request, key);

        assertEquals(201, first.status());
        assertEquals(201, retry.status());
        assertEquals(first.text("/id"), retry.text("/id"));
    }

    @Test
    void anIdempotencyKeyCannotBeReusedForADifferentRequest() {
        String key = UUID.randomUUID().toString();
        api.post("/v1/accounts", Map.of("customerId", UUID.randomUUID().toString(), "type", "CHECKING", "currency", "USD"), key);

        HttpJson.Response other = api.post(
                "/v1/accounts", Map.of("customerId", UUID.randomUUID().toString(), "type", "SAVINGS", "currency", "USD"), key);

        assertEquals(422, other.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", other.text("/code"));
    }

    @Test
    void aWriteWithoutAnIdempotencyKeyIsRefused() {
        HttpJson.Response response = api.post(
                "/v1/accounts", Map.of("customerId", UUID.randomUUID().toString(), "type", "CHECKING", "currency", "USD"), null);

        assertEquals(400, response.status());
        assertEquals("MISSING_HEADER", response.text("/code"));
        assertTrue(response.header("Content-Type").startsWith("application/problem+json"));
    }

    @Test
    void aBadRequestSaysWhichFieldsAreWrong() {
        HttpJson.Response response = api.post("/v1/accounts", Map.of("customerId", UUID.randomUUID().toString(), "currency", "usd"));

        assertEquals(400, response.status());
        assertEquals("VALIDATION_FAILED", response.text("/code"));
        assertTrue(response.body().at("/fields").has("type"));
        assertTrue(response.body().at("/fields").has("currency"));
    }

    @Test
    void anUnknownAccountIsNotFound() {
        HttpJson.Response response = api.get("/v1/accounts/" + UUID.randomUUID());

        assertEquals(404, response.status());
        assertEquals("ACCOUNT_NOT_FOUND", response.text("/code"));
    }

    @Test
    void aTransferMovesMoneyFromOneAccountToAnother() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();

        HttpJson.Response posted = transfer("pay:" + UUID.randomUUID(), alice, bob, "30.25");

        assertEquals(201, posted.status(), posted.body().toString());
        assertEquals("69.75", ledgerBalance(alice));
        assertEquals("30.25", ledgerBalance(bob));
    }

    @Test
    void theSameReferenceIsPostedOnce() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String reference = "pay:" + UUID.randomUUID();

        HttpJson.Response first = transfer(reference, alice, bob, "10.00");
        HttpJson.Response again = transfer(reference, alice, bob, "10.00");

        assertEquals(201, first.status());
        assertEquals(200, again.status());
        assertEquals(first.text("/id"), again.text("/id"));
        assertEquals("90.00", ledgerBalance(alice));

        HttpJson.Response different = transfer(reference, alice, bob, "11.00");
        assertEquals(409, different.status());
        assertEquals("DUPLICATE_REFERENCE", different.text("/code"));
    }

    @Test
    void aTransferLargerThanTheBalanceIsRefusedAndNothingMoves() {
        String alice = openAccountWith("20.00");
        String bob = openAccount();

        HttpJson.Response response = transfer("pay:" + UUID.randomUUID(), alice, bob, "20.01");

        assertEquals(422, response.status());
        assertEquals("INSUFFICIENT_FUNDS", response.text("/code"));
        assertEquals("20.00", ledgerBalance(alice));
        assertEquals("0.00", ledgerBalance(bob));
    }

    @Test
    void anEntryWhoseSidesDoNotMatchIsRefused() {
        String alice = openAccountWith("20.00");
        String bob = openAccount();

        HttpJson.Response response = api.post(
                "/v1/journal-entries",
                Map.of(
                        "reference", "pay:" + UUID.randomUUID(),
                        "postings", List.of(line(alice, "DEBIT", "10.00"), line(bob, "CREDIT", "9.99"))));

        assertEquals(422, response.status());
        assertEquals("UNBALANCED_ENTRY", response.text("/code"));
        assertEquals("20.00", ledgerBalance(alice));
    }

    @Test
    void anAmountWithTooManyDecimalsIsRefused() {
        String alice = openAccountWith("20.00");
        String bob = openAccount();

        HttpJson.Response response = transfer("pay:" + UUID.randomUUID(), alice, bob, "1.005");

        assertEquals(400, response.status());
        assertEquals("INVALID_MONEY", response.text("/code"));
    }

    @Test
    void aHoldReservesMoneyAndAReleaseGivesItBack() {
        String alice = openAccountWith("100.00");

        HttpJson.Response hold = api.post(
                "/v1/holds", Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID()));

        assertEquals(201, hold.status(), hold.body().toString());
        assertEquals("ACTIVE", hold.text("/status"));
        assertEquals("100.00", ledgerBalance(alice));
        assertEquals("60.00", availableBalance(alice));

        HttpJson.Response released = api.post("/v1/holds/" + hold.text("/id") + "/release", null);
        assertEquals(200, released.status());
        assertEquals("RELEASED", released.text("/status"));
        assertEquals("100.00", availableBalance(alice));

        // Releasing again is fine and changes nothing, so a caller can always retry.
        assertEquals(200, api.post("/v1/holds/" + hold.text("/id") + "/release", null).status());
        assertEquals("100.00", availableBalance(alice));
    }

    @Test
    void heldMoneyCannotBeSpentBySomethingElse() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        api.post("/v1/holds", Map.of("accountId", alice, "amount", money("80.00"), "reference", "payment:" + UUID.randomUUID()));

        HttpJson.Response response = transfer("pay:" + UUID.randomUUID(), alice, bob, "20.01");

        assertEquals(422, response.status());
        assertEquals("INSUFFICIENT_FUNDS", response.text("/code"));
    }

    @Test
    void askingForTheSameHoldTwiceReservesTheMoneyOnce() {
        String alice = openAccountWith("100.00");
        Map<String, Object> request =
                Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID());

        HttpJson.Response first = api.post("/v1/holds", request);
        HttpJson.Response again = api.post("/v1/holds", request);

        assertEquals(201, first.status());
        assertEquals(200, again.status());
        assertEquals(first.text("/id"), again.text("/id"));
        assertEquals("60.00", availableBalance(alice));
    }

    @Test
    void aHoldThatWasReleasedIsNotHandedOutAgain() {
        String alice = openAccountWith("100.00");
        Map<String, Object> request =
                Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID());
        String holdId = api.post("/v1/holds", request).text("/id");
        api.post("/v1/holds/" + holdId + "/release", null);

        // The same request again must not answer "here is your hold". Nothing is reserved any more.
        HttpJson.Response again = api.post("/v1/holds", request);

        assertEquals(409, again.status());
        assertEquals("HOLD_NOT_ACTIVE", again.text("/code"));
        assertEquals("100.00", availableBalance(alice));
    }

    @Test
    void anAmountOfZeroOrLessIsRefusedAsABadRequest() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();

        HttpJson.Response negative = transfer("negative:" + UUID.randomUUID(), alice, bob, "-5.00");
        HttpJson.Response zeroHold = api.post(
                "/v1/holds", Map.of("accountId", alice, "amount", money("0.00"), "reference", "zero:" + UUID.randomUUID()));

        assertEquals(400, negative.status());
        assertEquals("INVALID_MONEY", negative.text("/code"));
        assertEquals(400, zeroHold.status());
        assertEquals("100.00", ledgerBalance(alice));
    }

    @Test
    void theBanksOwnAccountsCannotBeOpenedThroughTheApi() {
        // An internal account has no overdraft limit. Whoever could open one could create money.
        HttpJson.Response response = api.post("/v1/accounts", Map.of("type", "INTERNAL", "currency", "USD"));

        assertEquals(400, response.status());
        assertEquals("VALIDATION_FAILED", response.text("/code"));
    }

    @Test
    void capturingAHoldPostsTheEntryAndEndsTheHoldTogether() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String holdId = api.post(
                        "/v1/holds", Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID()))
                .text("/id");

        HttpJson.Response posted = api.post(
                "/v1/journal-entries",
                Map.of(
                        "reference", "pay:" + UUID.randomUUID(),
                        "holdId", holdId,
                        "postings", List.of(line(alice, "DEBIT", "40.00"), line(bob, "CREDIT", "40.00"))));

        assertEquals(201, posted.status(), posted.body().toString());
        assertEquals("60.00", ledgerBalance(alice));
        assertEquals("60.00", availableBalance(alice));
        assertEquals("40.00", ledgerBalance(bob));
        assertEquals("CAPTURED", api.get("/v1/holds/" + holdId).text("/status"));
    }

    @Test
    void aHoldIsOnlyEndedByAnEntryThatSpendsExactlyWhatItReserved() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String holdId = api.post(
                        "/v1/holds", Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID()))
                .text("/id");

        // An entry that only pays 5.00 into the account, and one that takes less than was reserved.
        HttpJson.Response unrelated = api.post(
                "/v1/journal-entries",
                Map.of(
                        "reference", "pay:" + UUID.randomUUID(),
                        "holdId", holdId,
                        "postings", List.of(line(bob, "DEBIT", "5.00"), line(alice, "CREDIT", "5.00"))));
        HttpJson.Response tooSmall = api.post(
                "/v1/journal-entries",
                Map.of(
                        "reference", "pay:" + UUID.randomUUID(),
                        "holdId", holdId,
                        "postings", List.of(line(alice, "DEBIT", "10.00"), line(bob, "CREDIT", "10.00"))));

        assertEquals(422, unrelated.status(), unrelated.body().toString());
        assertEquals("HOLD_MISMATCH", unrelated.text("/code"));
        assertEquals(422, tooSmall.status(), tooSmall.body().toString());
        assertEquals("ACTIVE", api.get("/v1/holds/" + holdId).text("/status"));
        assertEquals("60.00", availableBalance(alice));
        assertEquals("100.00", ledgerBalance(alice));
    }

    @Test
    void aRepeatedEntryIsNotMistakenForTheOneThatEndedAHold() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String reference = "pay:" + UUID.randomUUID();
        List<Map<String, Object>> lines = List.of(line(alice, "DEBIT", "40.00"), line(bob, "CREDIT", "40.00"));
        // First posted with no hold. Then a hold is placed, and the same entry is sent again naming it.
        assertEquals(201, api.post("/v1/journal-entries", Map.of("reference", reference, "postings", lines)).status());
        String holdId = api.post(
                        "/v1/holds", Map.of("accountId", alice, "amount", money("40.00"), "reference", "payment:" + UUID.randomUUID()))
                .text("/id");

        HttpJson.Response repeat =
                api.post("/v1/journal-entries", Map.of("reference", reference, "holdId", holdId, "postings", lines));

        assertEquals(409, repeat.status(), repeat.body().toString());
        assertEquals("DUPLICATE_REFERENCE", repeat.text("/code"));
        assertEquals("ACTIVE", api.get("/v1/holds/" + holdId).text("/status"));
    }

    @Test
    void aHoldLargerThanTheBalanceIsRefused() {
        String alice = openAccountWith("10.00");

        HttpJson.Response response = api.post(
                "/v1/holds", Map.of("accountId", alice, "amount", money("10.01"), "reference", "payment:" + UUID.randomUUID()));

        assertEquals(422, response.status());
        assertEquals("INSUFFICIENT_FUNDS", response.text("/code"));
    }

    @Test
    void aFrozenAccountReceivesButDoesNotSend() {
        String alice = openAccountWith("50.00");
        String bob = openAccountWith("50.00");

        assertEquals("FROZEN", api.post("/v1/accounts/" + alice + "/freeze", null).text("/status"));

        assertEquals(201, transfer("pay:" + UUID.randomUUID(), bob, alice, "5.00").status());
        HttpJson.Response out = transfer("pay:" + UUID.randomUUID(), alice, bob, "5.00");
        assertEquals(422, out.status());
        assertEquals("ACCOUNT_FROZEN", out.text("/code"));

        assertEquals("ACTIVE", api.post("/v1/accounts/" + alice + "/unfreeze", null).text("/status"));
        assertEquals(201, transfer("pay:" + UUID.randomUUID(), alice, bob, "5.00").status());
    }

    @Test
    void anAccountWithMoneyInItCannotBeClosed() {
        String alice = openAccountWith("1.00");
        String bob = openAccount();

        HttpJson.Response refused = api.post("/v1/accounts/" + alice + "/close", null);
        assertEquals(409, refused.status());
        assertEquals("ACCOUNT_NOT_EMPTY", refused.text("/code"));

        transfer("pay:" + UUID.randomUUID(), alice, bob, "1.00");
        assertEquals("CLOSED", api.post("/v1/accounts/" + alice + "/close", null).text("/status"));
    }

    @Test
    void entriesOfAnAccountComeNewestFirstInPages() {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        List<String> references = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String reference = "pay:" + i + ":" + UUID.randomUUID();
            references.add(reference);
            transfer(reference, alice, bob, "1.00");
        }

        HttpJson.Response first = api.get("/v1/accounts/" + alice + "/entries?limit=4");
        assertEquals(200, first.status());
        assertEquals(4, first.body().at("/items").size());
        assertEquals(references.get(4), first.text("/items/0/reference"));
        assertEquals(references.get(1), first.text("/items/3/reference"));
        assertNotEquals("", first.text("/nextCursor"));

        HttpJson.Response second = api.get("/v1/accounts/" + alice + "/entries?limit=4&cursor=" + first.text("/nextCursor"));
        // The last page holds the first payment and the opening deposit, and has no cursor.
        assertEquals(2, second.body().at("/items").size());
        assertEquals(references.get(0), second.text("/items/0/reference"));
        assertEquals("", second.text("/nextCursor"));

        assertEquals(400, api.get("/v1/accounts/" + alice + "/entries?cursor=not-a-cursor").status());
    }
}
