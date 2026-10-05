package com.sainagesh.bank.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.testing.HttpJson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * What happens when an account stays locked. The wait is set very short here, so the test does not
 * have to sit through the real five seconds.
 */
@TestPropertySource(properties = "bank.ledger.lock-timeout=200ms")
class LedgerLockTimeoutIT extends LedgerIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void aPostingGivesUpOnALockedAccountAndWorksOnceTheLockIsGone() throws Exception {
        String alice = openAccountWith("100.00");
        String bob = openAccount();
        String reference = "locked:" + UUID.randomUUID();

        try (Connection other = dataSource.getConnection()) {
            // Someone else holds Alice's row, the way a long transaction or a stuck session would.
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement("select id from account where id = ? for update")) {
                lock.setObject(1, UUID.fromString(alice));
                lock.executeQuery();
            }

            long started = System.nanoTime();
            HttpJson.Response busy = transfer(reference, alice, bob, "10.00");
            long waitedMillis = (System.nanoTime() - started) / 1_000_000;

            // It did not hang, it did not move money, and it told the caller to try again.
            assertEquals(409, busy.status(), busy.body().toString());
            assertEquals("CONCURRENT_UPDATE", busy.text("/code"));
            assertEquals("1", busy.header("Retry-After"));
            assertTrue(waitedMillis < 10_000, "waited " + waitedMillis + " ms");

            other.rollback();
        }

        // The same request, sent again as the answer asked, now goes through. Once.
        assertEquals(201, transfer(reference, alice, bob, "10.00").status());
        assertEquals("90.00", ledgerBalance(alice));
        assertEquals("10.00", ledgerBalance(bob));
    }
}
