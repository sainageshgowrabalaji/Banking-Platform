package com.sainagesh.bank.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sainagesh.bank.testing.Contract;
import com.sainagesh.bank.testing.HttpJson;
import com.sainagesh.bank.testing.RequiresInfrastructure;
import com.sainagesh.bank.testing.TestInfrastructure;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of the ledger's integration tests. It starts the whole service on a random port against a real
 * PostgreSQL and a real Kafka, and gives the tests a client and a few shortcuts.
 */
@RequiresInfrastructure
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class LedgerIntegrationTest {

    /** The bank's cash account, created by the V2 migration. Deposits come out of it. */
    static final String CASH = "00000000-0000-0000-0000-000000000100";

    @LocalServerPort
    int port;

    /** Every response in these tests is compared with the contract other teams build against. */
    static final Contract CONTRACT = Contract.openApi("ledger-v1.yaml");

    HttpJson api;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.postgres(registry, "ledger");
        TestInfrastructure.kafka(registry);
    }

    @BeforeEach
    void client() {
        api = new HttpJson("http://localhost:" + port).checkedAgainst(CONTRACT);
    }

    static Map<String, Object> money(String amount) {
        return Map.of("amount", amount, "currency", "USD");
    }

    static Map<String, Object> line(String accountId, String side, String amount) {
        return Map.of("accountId", accountId, "side", side, "amount", money(amount));
    }

    /** Opens a checking account for a new customer and returns its id. */
    String openAccount() {
        HttpJson.Response response = api.post(
                "/v1/accounts", Map.of("customerId", UUID.randomUUID().toString(), "type", "CHECKING", "currency", "USD"));
        assertEquals(201, response.status(), response.body().toString());
        return response.text("/id");
    }

    /** Puts money into an account, the way a cash deposit would. */
    void deposit(String accountId, String amount) {
        HttpJson.Response response = api.post(
                "/v1/journal-entries",
                Map.of(
                        "reference", "deposit:" + UUID.randomUUID(),
                        "postings", List.of(line(CASH, "DEBIT", amount), line(accountId, "CREDIT", amount))));
        assertEquals(201, response.status(), response.body().toString());
    }

    String openAccountWith(String amount) {
        String id = openAccount();
        deposit(id, amount);
        return id;
    }

    HttpJson.Response transfer(String reference, String from, String to, String amount) {
        return api.post(
                "/v1/journal-entries",
                Map.of("reference", reference, "postings", List.of(line(from, "DEBIT", amount), line(to, "CREDIT", amount))));
    }

    String ledgerBalance(String accountId) {
        return api.get("/v1/accounts/" + accountId + "/balance").text("/ledger/amount");
    }

    String availableBalance(String accountId) {
        return api.get("/v1/accounts/" + accountId + "/balance").text("/available/amount");
    }
}
