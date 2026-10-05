package com.sainagesh.bank.payments;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sainagesh.bank.testing.Contract;
import com.sainagesh.bank.testing.HttpJson;
import com.sainagesh.bank.testing.RequiresInfrastructure;
import com.sainagesh.bank.testing.TestInfrastructure;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the payments integration tests. The whole service runs on a random port against a real
 * PostgreSQL and a real Kafka. The ledger is a small fake that speaks the same HTTP API.
 */
@RequiresInfrastructure
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PaymentsIntegrationTest {

    static final String ACH_SETTLEMENT = "00000000-0000-0000-0000-000000000101";
    static final String INSTANT_SETTLEMENT = "00000000-0000-0000-0000-000000000102";

    static final FakeLedger LEDGER = new FakeLedger();

    static {
        LEDGER.internalAccount(ACH_SETTLEMENT);
        LEDGER.internalAccount(INSTANT_SETTLEMENT);
    }

    @LocalServerPort
    int port;

    /** Every response in these tests is compared with the contract other teams build against. */
    static final Contract CONTRACT = Contract.openApi("payments-v1.yaml");

    HttpJson api;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.postgres(registry, "payments");
        TestInfrastructure.kafka(registry);
        registry.add("bank.payments.ledger.url", LEDGER::url);
        registry.add("bank.payments.ledger.read-timeout", () -> "PT2S");
        // The simulated networks answer in a second or two, so the tests do not wait for tomorrow.
        registry.add("bank.payments.ach.batch-interval", () -> "PT1S");
        registry.add("bank.payments.ach.settle-after", () -> "PT1S");
        registry.add("bank.payments.ach.settle-check", () -> "PT0.5S");
        registry.add("bank.payments.ach.poll-interval", () -> "PT0.5S");
        registry.add("bank.payments.worker.poll-interval", () -> "PT0.2S");
        // Give up on a payment that keeps failing after three tries, so the test of that is quick.
        registry.add("bank.payments.park-after", () -> "3");
    }

    @BeforeEach
    void client() {
        api = new HttpJson("http://localhost:" + port).checkedAgainst(CONTRACT);
        LEDGER.down(false);
    }

    static Map<String, Object> money(String amount) {
        return Map.of("amount", amount, "currency", "USD");
    }

    static Map<String, Object> payment(String debtor, String rail, String creditorName, String creditorAccount, String amount) {
        Map<String, Object> creditor = new LinkedHashMap<>();
        creditor.put("name", creditorName);
        creditor.put("accountNumber", creditorAccount);
        if (!rail.equals("BOOK")) {
            creditor.put("routingNumber", "021000021");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("debtorAccountId", debtor);
        request.put("creditor", creditor);
        request.put("amount", money(amount));
        request.put("rail", rail);
        request.put("endToEndId", "INV-42");
        request.put("remittanceInformation", "Invoice 42");
        return request;
    }

    String statusOf(String paymentId) {
        return api.get("/v1/payments/" + paymentId).text("/status");
    }

    /** Waits for a payment to reach a status. The saga worker moves it there in the background. */
    HttpJson.Response awaitStatus(String paymentId, String status) {
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertEquals(status, statusOf(paymentId)));
        return api.get("/v1/payments/" + paymentId);
    }
}
