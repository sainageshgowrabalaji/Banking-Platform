package com.sainagesh.bank.payments;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.testing.HttpJson;
import com.sainagesh.bank.testing.KafkaProbe;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Whole payments, from the request to the final status, on each rail and on each way to fail. */
class PaymentFlowsIT extends PaymentsIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void aBookPaymentSettlesAtOnceAndMovesTheMoney() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "30.25"));

        assertEquals(202, response.status(), response.body().toString());
        assertEquals("/v1/payments/" + response.text("/id"), response.header("Location"));
        assertEquals("ACSC", response.text("/status"));
        assertNotEquals("", response.text("/settledAt"));
        assertEquals("69.75", LEDGER.balance(alice));
        assertEquals("69.75", LEDGER.available(alice));
        assertEquals("30.25", LEDGER.balance(bob));
    }

    @Test
    void aPaymentLargerThanTheBalanceIsRejectedWithTheIsoReason() {
        String alice = LEDGER.account("10.00");
        String bob = LEDGER.account("0.00");

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "10.01"));

        assertEquals(202, response.status());
        assertEquals("RJCT", response.text("/status"));
        assertEquals("AM04", response.text("/reasonCode"));
        assertEquals("10.00", LEDGER.available(alice));
    }

    @Test
    void aPaymentToABlockedNameIsStoppedAndTheReservedMoneyIsGivenBack() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "A Blocked Person Ltd", bob, "40.00"));

        assertEquals("RJCT", response.text("/status"));
        assertEquals("RR04", response.text("/reasonCode"));
        // The hold was placed before the check, and the undo step released it.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("100.00", LEDGER.available(alice)));
        assertEquals("100.00", LEDGER.balance(alice));
        assertEquals("0.00", LEDGER.balance(bob));
    }

    @Test
    void aBookPaymentToAnAccountThatDoesNotExistIsRejectedAndReleased() {
        String alice = LEDGER.account("100.00");

        HttpJson.Response response =
                api.post("/v1/payments", payment(alice, "BOOK", "Nobody", UUID.randomUUID().toString(), "40.00"));

        assertEquals("RJCT", response.text("/status"));
        assertEquals("AC01", response.text("/reasonCode"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("100.00", LEDGER.available(alice)));
    }

    @Test
    void anInstantPaymentIsSentAsPacs008AndSettledByThePacs002Answer() {
        String alice = LEDGER.account("500.00");
        // Every test shares the settlement account, so look at what this payment adds to it.
        BigDecimal settledBefore = new BigDecimal(LEDGER.balance(INSTANT_SETTLEMENT));

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "INSTANT", "Jane Doe", "123456789", "125.50"));

        assertEquals("ACSC", response.text("/status"), response.body().toString());
        assertEquals("374.50", LEDGER.balance(alice));
        assertEquals(new BigDecimal("125.50"), new BigDecimal(LEDGER.balance(INSTANT_SETTLEMENT)).subtract(settledBefore));

        HttpJson.Response messages = api.get("/v1/payments/" + response.text("/id") + "/messages");
        assertEquals(2, messages.body().size());
        assertEquals("OUT", messages.text("/0/direction"));
        assertEquals("pacs.008.001.08", messages.text("/0/type"));
        assertTrue(messages.text("/0/body").contains("<IntrBkSttlmAmt Ccy=\"USD\">125.50</IntrBkSttlmAmt>"));
        assertTrue(messages.text("/0/body").contains("<EndToEndId>INV-42</EndToEndId>"));
        assertEquals("IN", messages.text("/1/direction"));
        assertEquals("pacs.002.001.10", messages.text("/1/type"));
        assertTrue(messages.text("/1/body").contains("<TxSts>ACSC</TxSts>"));
    }

    @Test
    void anInstantPaymentTheNetworkRefusesIsRejectedAndReleased() {
        String alice = LEDGER.account("500.00");

        // The simulated network treats an account ending in 0000 as closed.
        HttpJson.Response response = api.post("/v1/payments", payment(alice, "INSTANT", "Jane Doe", "99990000", "125.50"));

        assertEquals("RJCT", response.text("/status"));
        assertEquals("AC04", response.text("/reasonCode"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("500.00", LEDGER.available(alice)));
        assertEquals("500.00", LEDGER.balance(alice));
    }

    @Test
    void anInstantPaymentOverTheRailLimitIsRejected() {
        String alice = LEDGER.account("900000.00");

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "INSTANT", "Jane Doe", "123456789", "500000.01"));

        assertEquals("RJCT", response.text("/status"));
        assertEquals("AM02", response.text("/reasonCode"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("900000.00", LEDGER.available(alice)));
    }

    @Test
    void anAchPaymentWaitsForItsBatchAndSettlesLater() {
        String alice = LEDGER.account("300.00");

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "ACH", "Jane Doe", "123456789", "80.00"));

        // It is on its way but has not settled. The money is reserved, not yet gone.
        assertEquals("ACSP", response.text("/status"), response.body().toString());
        assertNotEquals("", response.text("/settlementDate"));
        assertEquals("300.00", LEDGER.balance(alice));
        assertEquals("220.00", LEDGER.available(alice));

        HttpJson.Response settled = awaitStatus(response.text("/id"), "ACSC");

        assertNotEquals("", settled.text("/settledAt"));
        assertEquals("220.00", LEDGER.balance(alice));
        assertEquals("220.00", LEDGER.available(alice));
    }

    @Test
    void anAchPaymentTheReceivingBankReturnsIsRejectedAndReleased() {
        String alice = LEDGER.account("300.00");

        // The simulated network returns an entry for an account ending in 1111 as "no such account".
        HttpJson.Response response = api.post("/v1/payments", payment(alice, "ACH", "Jane Doe", "55551111", "80.00"));
        assertEquals("ACSP", response.text("/status"));

        HttpJson.Response returned = awaitStatus(response.text("/id"), "RJCT");

        assertEquals("AC01", returned.text("/reasonCode"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("300.00", LEDGER.available(alice)));
        assertEquals("300.00", LEDGER.balance(alice));
    }

    @Test
    void aRetryWithTheSameIdempotencyKeyDoesNotPayTwice() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        String key = UUID.randomUUID().toString();

        HttpJson.Response first = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "25.00"), key);
        HttpJson.Response retry = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "25.00"), key);

        assertEquals(202, retry.status());
        assertEquals(first.text("/id"), retry.text("/id"));
        assertEquals("ACSC", retry.text("/status"));
        assertEquals("75.00", LEDGER.balance(alice));
        assertEquals("25.00", LEDGER.balance(bob));
    }

    @Test
    void theSameKeyWithADifferentPaymentIsRefused() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        String key = UUID.randomUUID().toString();
        api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "25.00"), key);

        HttpJson.Response other = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "26.00"), key);

        assertEquals(422, other.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", other.text("/code"));
        assertEquals("75.00", LEDGER.balance(alice));
    }

    @Test
    void anAchPaymentCanBeCancelledOnlyBeforeItIsSent() {
        String alice = LEDGER.account("300.00");
        HttpJson.Response sent = api.post("/v1/payments", payment(alice, "ACH", "Jane Doe", "123456789", "80.00"));
        assertEquals("ACSP", sent.text("/status"));

        HttpJson.Response refused = api.post("/v1/payments/" + sent.text("/id") + "/cancel", null);

        assertEquals(409, refused.status());
        assertEquals("PAYMENT_STATE_CONFLICT", refused.text("/code"));
        awaitStatus(sent.text("/id"), "ACSC");
    }

    @Test
    void whenTheLedgerIsDownThePaymentIsKeptAndFinishedLater() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        LEDGER.down(true);

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "10.00"));

        // Accepted for processing, even though nothing could be done yet.
        assertEquals(202, response.status());
        assertEquals("RCVD", response.text("/status"));

        // While the ledger is down the payment can still be cancelled, with nothing to undo.
        String other = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "5.00")).text("/id");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals("CANC", api.post("/v1/payments/" + other + "/cancel", null).text("/status")));

        LEDGER.down(false);

        awaitStatus(response.text("/id"), "ACSC");
        assertEquals("90.00", LEDGER.balance(alice));
        assertEquals("10.00", LEDGER.balance(bob));
        assertEquals("CANC", statusOf(other));
    }

    @Test
    void eachStepOfAPaymentIsAnnouncedOnKafkaInOrder() {
        try (KafkaProbe probe = new KafkaProbe("bank.payments.v1")) {
            String alice = LEDGER.account("100.00");
            String bob = LEDGER.account("0.00");
            String id = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "12.00")).text("/id");

            ConsumerRecord<String, String> settled = probe.awaitRecord(
                    record -> id.equals(record.key()) && record.value().contains("bank.payments.settled"),
                    Duration.ofSeconds(30));

            assertTrue(settled.value().contains("\"status\":\"ACSC\""), settled.value());
            assertTrue(settled.value().contains("\"amount\":\"12.00\""), settled.value());
            // Accepted comes before settled, on the same partition, because both carry the payment id as key.
            ConsumerRecord<String, String> accepted = probe.awaitRecord(
                    record -> id.equals(record.key()) && record.value().contains("bank.payments.accepted"),
                    Duration.ofSeconds(5));
            assertEquals(accepted.partition(), settled.partition());
            assertTrue(accepted.offset() < settled.offset());
        }
    }

    @Test
    void cancellingFindsAndReleasesAHoldWhoseAnswerWasLost() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        // The ledger reserves the money, but its answer never arrives. The payment has no hold id.
        LEDGER.loseNextHoldAnswer();
        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "30.00"));
        assertEquals("RCVD", response.text("/status"));
        assertEquals("70.00", LEDGER.available(alice));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals("CANC", api.post("/v1/payments/" + response.text("/id") + "/cancel", null).text("/status")));

        // The undo step asked the ledger for the hold again, learned its id and gave the money back.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("100.00", LEDGER.available(alice)));
        assertEquals("100.00", LEDGER.balance(alice));
        assertEquals("CANC", statusOf(response.text("/id")));
    }

    @Test
    void aPaymentThatLeftTheBankIsNeverCalledRejectedAndIsParkedForAPerson() {
        String alice = LEDGER.account("500.00");
        // The other bank will say yes and has the money. Then the ledger refuses to post the entry,
        // as it would if the account were frozen in between.
        LEDGER.refusePostsFrom(alice, true);

        String id = api.post("/v1/payments", payment(alice, "INSTANT", "Jane Doe", "123456789", "125.50")).text("/id");

        // Three failed tries, then the saga stops and waits for a person.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(
                1L,
                jdbc.sql("select count(*) from payment where id = :id::uuid and parked_at is not null")
                        .param("id", id)
                        .query(Long.class)
                        .single()));
        assertEquals("ACSP", statusOf(id));
        assertEquals("374.50", LEDGER.available(alice));

        // The person fixes the cause and lets the saga try again, with the statement in V1__payments.sql.
        LEDGER.refusePostsFrom(alice, false);
        jdbc.sql("update payment set parked_at = null, failures = 0, attempts = 0, next_attempt_at = now() where id = :id::uuid")
                .param("id", id)
                .update();

        awaitStatus(id, "ACSC");
        assertEquals("374.50", LEDGER.balance(alice));
    }

    @Test
    void aBookPaymentTheLedgerWillNotPostIsRejectedBecauseNothingLeftTheBank() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        LEDGER.refusePostsFrom(alice, true);

        HttpJson.Response response = api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "40.00"));

        assertEquals("RJCT", response.text("/status"));
        assertEquals("AC06", response.text("/reasonCode"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("100.00", LEDGER.available(alice)));
        assertEquals("0.00", LEDGER.balance(bob));
    }

    @Test
    void anIsoMessageWithATrickAmountOrSeveralPaymentsIsRefused() throws Exception {
        String alice = LEDGER.account("2000.00");
        String template = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr><MsgId>ACME-2</MsgId></GrpHdr>
                    <PmtInf>
                      <DbtrAcct><Id><Othr><Id>%s</Id></Othr></Id></DbtrAcct>
                      %s
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>
                """;
        String transaction = """
                <CdtTrfTxInf>
                  <Amt><InstdAmt Ccy="USD">%s</InstdAmt></Amt>
                  <CdtrAgt><FinInstnId><ClrSysMmbId><MmbId>021000021</MmbId></ClrSysMmbId></FinInstnId></CdtrAgt>
                  <Cdtr><Nm>Supplier Inc</Nm></Cdtr>
                  <CdtrAcct><Id><Othr><Id>987654321</Id></Othr></Id></CdtrAcct>
                </CdtTrfTxInf>
                """;

        // A number written so that working it out would need more memory than the service has.
        java.net.http.HttpResponse<String> huge = postXml(template.formatted(alice, transaction.formatted("1E400000000")));
        assertEquals(400, huge.statusCode(), huge.body());
        assertTrue(huge.body().contains("INVALID_MESSAGE"), huge.body());

        // Two payments in one message. Taking one and dropping the other in silence would be worse than refusing.
        java.net.http.HttpResponse<String> two =
                postXml(template.formatted(alice, transaction.formatted("10.00") + transaction.formatted("20.00")));
        assertEquals(400, two.statusCode(), two.body());

        assertEquals("2000.00", LEDGER.available(alice));
    }

    private java.net.http.HttpResponse<String> postXml(String xml) throws Exception {
        return java.net.http.HttpClient.newHttpClient()
                .send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/v1/payments"))
                                .header("Content-Type", "application/xml")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(xml))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aPaymentCanArriveAsAnIsoPain001Message() throws Exception {
        String alice = LEDGER.account("2000.00");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr><MsgId>ACME-1</MsgId><CreDtTm>2026-10-05T14:00:00Z</CreDtTm><NbOfTxs>1</NbOfTxs></GrpHdr>
                    <PmtInf>
                      <PmtTpInf><LclInstrm><Prtry>INSTANT</Prtry></LclInstrm></PmtTpInf>
                      <DbtrAcct><Id><Othr><Id>%s</Id></Othr></Id></DbtrAcct>
                      <CdtTrfTxInf>
                        <PmtId><EndToEndId>INV-77</EndToEndId></PmtId>
                        <Amt><InstdAmt Ccy="USD">980.00</InstdAmt></Amt>
                        <CdtrAgt><FinInstnId><ClrSysMmbId><MmbId>021000021</MmbId></ClrSysMmbId></FinInstnId></CdtrAgt>
                        <Cdtr><Nm>Supplier Inc</Nm></Cdtr>
                        <CdtrAcct><Id><Othr><Id>987654321</Id></Othr></Id></CdtrAcct>
                      </CdtTrfTxInf>
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>
                """.formatted(alice);

        java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/v1/payments"))
                                .header("Content-Type", "application/xml")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(xml))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(202, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"ACSC\""), response.body());
        assertTrue(response.body().contains("\"endToEndId\":\"INV-77\""), response.body());
        assertEquals("1020.00", LEDGER.balance(alice));
    }

    @Test
    void badRequestsAreRefusedBeforeAnythingIsStored() {
        String alice = LEDGER.account("100.00");

        HttpJson.Response noRouting = api.post("/v1/payments", java.util.Map.of(
                "debtorAccountId", alice,
                "creditor", java.util.Map.of("name", "Jane", "accountNumber", "123456789"),
                "amount", money("5.00"),
                "rail", "ACH"));
        assertEquals(400, noRouting.status());
        assertEquals("VALIDATION_FAILED", noRouting.text("/code"));

        HttpJson.Response sameAccount = api.post("/v1/payments", payment(alice, "BOOK", "Me", alice, "5.00"));
        assertEquals(400, sameAccount.status());

        // A control character would break the ISO 20022 message the name is written into.
        HttpJson.Response controlCharacter =
                api.post("/v1/payments", payment(alice, "INSTANT", "Jane\u0001Doe", "123456789", "5.00"));
        assertEquals(400, controlCharacter.status());

        HttpJson.Response noKey = api.post("/v1/payments", payment(alice, "BOOK", "Bob", LEDGER.account("0.00"), "5.00"), null);
        assertEquals(400, noKey.status());
        assertEquals("MISSING_HEADER", noKey.text("/code"));

        assertEquals(404, api.get("/v1/payments/" + UUID.randomUUID()).status());
    }

    @Test
    void paymentsOfAnAccountAreListedNewestFirstInPages() {
        String alice = LEDGER.account("100.00");
        String bob = LEDGER.account("0.00");
        for (int i = 1; i <= 3; i++) {
            api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, i + ".00"));
        }
        api.post("/v1/payments", payment(alice, "BOOK", "Bob", bob, "500.00"));

        HttpJson.Response first = api.get("/v1/payments?accountId=" + alice + "&limit=3");
        assertEquals(3, first.body().at("/items").size());
        assertEquals("500.00", first.text("/items/0/amount/amount"));
        assertEquals("RJCT", first.text("/items/0/status"));
        assertEquals("3.00", first.text("/items/1/amount/amount"));

        HttpJson.Response second = api.get("/v1/payments?accountId=" + alice + "&limit=3&cursor=" + first.text("/nextCursor"));
        assertEquals(1, second.body().at("/items").size());
        assertEquals("1.00", second.text("/items/0/amount/amount"));
        assertEquals("", second.text("/nextCursor"));

        HttpJson.Response settledOnly = api.get("/v1/payments?accountId=" + alice + "&status=ACSC");
        assertEquals(3, settledOnly.body().at("/items").size());
    }
}
