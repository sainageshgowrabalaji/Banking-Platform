package com.sainagesh.bank.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.testing.HttpJson;
import com.sainagesh.bank.testing.KafkaProbe;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The promises the ledger makes about money. Nothing is lost, nothing is created, nothing is rewritten. */
class LedgerSafetyIT extends LedgerIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void manyPaymentsAtOnceNeverOverdrawAnAccount() throws Exception {
        String alice = openAccountWith("100.00");
        String bob = openAccount();

        // Forty payments of 5.00 race for 100.00. Exactly twenty can succeed.
        List<Callable<Integer>> payments = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            payments.add(() -> transfer("race:" + UUID.randomUUID(), alice, bob, "5.00").status());
        }
        int succeeded = 0;
        int refused = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Integer> result : pool.invokeAll(payments)) {
                int status = result.get();
                if (status == 201) {
                    succeeded++;
                } else if (status == 422) {
                    refused++;
                }
            }
        }

        assertEquals(20, succeeded);
        assertEquals(20, refused);
        assertEquals("0.00", ledgerBalance(alice));
        assertEquals("100.00", ledgerBalance(bob));
    }

    @Test
    void transfersInBothDirectionsAtOnceKeepEveryCent() throws Exception {
        String alice = openAccountWith("500.00");
        String bob = openAccountWith("500.00");

        List<Callable<Integer>> payments = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            payments.add(() -> transfer("ab:" + UUID.randomUUID(), alice, bob, "1.00").status());
            payments.add(() -> transfer("ba:" + UUID.randomUUID(), bob, alice, "1.00").status());
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (Future<Integer> result : pool.invokeAll(payments)) {
                assertEquals(201, result.get());
            }
        }

        BigDecimal total = new BigDecimal(ledgerBalance(alice)).add(new BigDecimal(ledgerBalance(bob)));
        assertEquals(new BigDecimal("1000.00"), total);
        assertEquals("500.00", ledgerBalance(alice));
    }

    @Test
    void theRunningBalancesAlwaysEqualTheSumOfTheEntries() {
        String alice = openAccountWith("75.00");
        String bob = openAccount();
        transfer("pay:" + UUID.randomUUID(), alice, bob, "12.34");

        Long mismatches = jdbc.sql("select count(*) from account_balance_mismatch").query(Long.class).single();

        assertEquals(0, mismatches);
    }

    @Test
    void everyDebitInTheWholeLedgerHasAMatchingCredit() {
        String alice = openAccountWith("75.00");
        transfer("pay:" + UUID.randomUUID(), alice, openAccount(), "5.00");

        Long difference = jdbc.sql("""
                select coalesce(sum(case when side = 'DEBIT' then amount_minor else -amount_minor end), 0)
                from posting
                """)
                .query(Long.class)
                .single();

        assertEquals(0, difference);
    }

    @Test
    void thePostedJournalCannotBeEditedOrDeletedEvenWithDirectDatabaseAccess() {
        openAccountWith("10.00");

        assertThrows(DataAccessException.class, () -> jdbc.sql("update posting set amount_minor = amount_minor + 1").update());
        assertThrows(DataAccessException.class, () -> jdbc.sql("delete from posting").update());
        assertThrows(DataAccessException.class, () -> jdbc.sql("update journal_entry set reference = 'changed'").update());
    }

    @Test
    void theJournalCannotBeEmptiedWithTruncate() {
        openAccountWith("10.00");

        assertThrows(DataAccessException.class, () -> jdbc.sql("truncate posting").update());
        assertThrows(DataAccessException.class, () -> jdbc.sql("truncate journal_entry cascade").update());
    }

    @Test
    void theDatabaseItselfRefusesAnEntryWithNoLines() {
        assertThrows(
                DataAccessException.class,
                () -> jdbc.sql("insert into journal_entry (id, reference, posted_at) values (gen_random_uuid(), :ref, now())")
                        .param("ref", "empty:" + UUID.randomUUID())
                        .update());
    }

    @Test
    void theSameHoldAskedForTwiceAtTheSameMomentReservesTheMoneyOnce() throws Exception {
        // 100.00 available and a hold of 60.00. If both requests reserved, the second would be refused
        // for lack of money and the caller would think its payment could not be paid.
        String alice = openAccountWith("100.00");
        Map<String, Object> request =
                Map.of("accountId", alice, "amount", money("60.00"), "reference", "payment:" + UUID.randomUUID());

        List<Callable<HttpJson.Response>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> api.post("/v1/holds", request));
        }
        List<String> ids = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<HttpJson.Response> result : pool.invokeAll(calls)) {
                HttpJson.Response response = result.get();
                assertTrue(response.status() == 201 || response.status() == 200, response.body().toString());
                ids.add(response.text("/id"));
            }
        }

        assertEquals(1, ids.stream().distinct().count());
        assertEquals("40.00", availableBalance(alice));
    }

    @Test
    void theSameEntryPostedTwiceAtTheSameMomentMovesTheMoneyOnce() throws Exception {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String reference = "pay:" + UUID.randomUUID();

        List<Callable<HttpJson.Response>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // 70.00 twice would not fit, so a second posting would show up as a refusal.
            calls.add(() -> transfer(reference, alice, bob, "70.00"));
        }
        int created = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<HttpJson.Response> result : pool.invokeAll(calls)) {
                HttpJson.Response response = result.get();
                assertTrue(response.status() == 201 || response.status() == 200, response.body().toString());
                if (response.status() == 201) {
                    created++;
                }
            }
        }

        assertEquals(1, created);
        assertEquals("30.00", ledgerBalance(alice));
        assertEquals("70.00", ledgerBalance(bob));
    }

    @Test
    void theDatabaseItselfRefusesAnOverdrawnCustomerAccount() {
        String alice = openAccountWith("10.00");

        assertThrows(
                DataAccessException.class,
                () -> jdbc.sql("update account set ledger_balance_minor = -1 where id = :id::uuid")
                        .param("id", alice)
                        .update());
    }

    @Test
    void aPostedEntryIsAnnouncedOnKafka() {
        try (KafkaProbe probe = new KafkaProbe("bank.ledger.v1", "bank.accounts.v1")) {
            String alice = openAccountWith("50.00");
            String bob = openAccount();
            String reference = "pay:" + UUID.randomUUID();
            HttpJson.Response posted = transfer(reference, alice, bob, "7.50");

            ConsumerRecord<String, String> event = probe.awaitRecord(
                    record -> record.value().contains(reference), Duration.ofSeconds(30));

            assertEquals(posted.text("/id"), event.key());
            assertTrue(event.value().contains("\"type\":\"bank.ledger.entry-posted\""), event.value());
            assertTrue(event.value().contains("\"amount\":\"7.50\""), event.value());

            ConsumerRecord<String, String> opened = probe.awaitRecord(
                    record -> record.topic().equals("bank.accounts.v1") && alice.equals(record.key()), Duration.ofSeconds(30));
            assertTrue(opened.value().contains("\"type\":\"bank.accounts.opened\""), opened.value());
        }
    }
}
