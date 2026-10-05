package com.sainagesh.bank.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.testing.Contract;
import com.sainagesh.bank.testing.HttpJson;
import com.sainagesh.bank.testing.KafkaProbe;
import com.sainagesh.bank.testing.RequiresInfrastructure;
import com.sainagesh.bank.testing.TestInfrastructure;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Events go into Kafka, and messages for the customer come out, once each. */
@RequiresInfrastructure
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationFlowIT {

    @LocalServerPort
    int port;

    @Autowired
    KafkaTemplate<String, String> kafka;

    /** Every response in these tests is compared with the contract other teams build against. */
    static final Contract CONTRACT = Contract.openApi("notifications-v1.yaml");

    HttpJson api;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.postgres(registry, "notification");
        TestInfrastructure.kafka(registry);
        // Each test run gets its own consumer group, so it never picks up where an older run stopped.
        registry.add("spring.kafka.consumer.group-id", () -> "notification-it-" + UUID.randomUUID());
    }

    @BeforeEach
    void client() {
        api = new HttpJson("http://localhost:" + port).checkedAgainst(CONTRACT);
    }

    private static String paymentEvent(UUID eventId, String type, String account, String status, String reasonCode) {
        String reason = reasonCode == null ? "" : ",\"reasonCode\":\"" + reasonCode + "\"";
        return """
                {"id":"%s","type":"%s","source":"payments-service","subject":"%s","time":"%s",
                 "data":{"paymentId":"%s","debtorAccountId":"%s","amount":{"amount":"125.50","currency":"USD"},
                         "rail":"INSTANT","status":"%s","creditorName":"Jane Doe","somethingNew":"ignored"%s}}
                """.formatted(eventId, type, UUID.randomUUID(), Instant.now(), UUID.randomUUID(), account, status, reason);
    }

    private HttpJson.Response inbox(String account) {
        return api.get("/v1/notifications?accountId=" + account);
    }

    @Test
    void aSettledPaymentBecomesOneMessage() {
        String account = UUID.randomUUID().toString();

        kafka.send("bank.payments.v1", account, paymentEvent(UUID.randomUUID(), "bank.payments.settled", account, "ACSC", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1, inbox(account).body().size()));
        assertEquals("Payment sent", inbox(account).text("/0/title"));
        assertEquals("Your payment of 125.50 USD to Jane Doe has been sent.", inbox(account).text("/0/body"));
    }

    @Test
    void theSameEventDeliveredTwiceGivesOneMessage() {
        String account = UUID.randomUUID().toString();
        UUID eventId = UUID.randomUUID();
        String event = paymentEvent(eventId, "bank.payments.rejected", account, "RJCT", "AM04");

        kafka.send("bank.payments.v1", account, event);
        kafka.send("bank.payments.v1", account, event);
        // A later, different event proves that both copies above were already consumed.
        kafka.send("bank.payments.v1", account, paymentEvent(UUID.randomUUID(), "bank.payments.settled", account, "ACSC", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(2, inbox(account).body().size()));
        assertEquals("Payment sent", inbox(account).text("/0/title"));
        assertEquals("Payment not sent", inbox(account).text("/1/title"));
        assertTrue(inbox(account).text("/1/body").contains("not enough money"));
    }

    @Test
    void aNewAccountIsWelcomed() {
        String account = UUID.randomUUID().toString();
        String event = """
                {"id":"%s","type":"bank.accounts.opened","source":"ledger-service","subject":"%s","time":"%s",
                 "data":{"accountId":"%s","customerId":"%s","type":"CHECKING","currency":"USD","status":"ACTIVE"}}
                """.formatted(UUID.randomUUID(), account, Instant.now(), account, UUID.randomUUID());

        kafka.send("bank.accounts.v1", account, event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1, inbox(account).body().size()));
        assertEquals("Your new checking account is open and ready to use.", inbox(account).text("/0/body"));
    }

    @Test
    void anEventThatCannotBeReadIsParkedAndDoesNotBlockTheNextOne() {
        String account = UUID.randomUUID().toString();
        String broken = "this is not json " + UUID.randomUUID();

        try (KafkaProbe deadLetters = new KafkaProbe("bank.payments.v1.dlt")) {
            kafka.send("bank.payments.v1", account, broken);
            kafka.send("bank.payments.v1", account, paymentEvent(UUID.randomUUID(), "bank.payments.settled", account, "ACSC", null));

            // The good event behind the broken one is still handled.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1, inbox(account).body().size()));
            // And the broken one is waiting on the dead letter topic for a person to look at.
            ConsumerRecord<String, String> parked =
                    deadLetters.awaitRecord(record -> broken.equals(record.value()), Duration.ofSeconds(30));
            assertEquals(account, parked.key());
        }
    }
}
