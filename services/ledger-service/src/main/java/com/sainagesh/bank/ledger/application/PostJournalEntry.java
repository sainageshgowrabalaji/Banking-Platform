package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.domain.JournalEntry;
import com.sainagesh.bank.ledger.domain.Posting;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Posts a journal entry. This is the only way money moves in the ledger.
 *
 * <p>Two requests that touch the same account at the same moment are made to take turns. The
 * transaction locks the account rows first, always in id order, so the second request waits until the
 * first has committed and then sees the new balance. That is pessimistic locking, and it is the right
 * tool for a hot row such as a busy account.
 *
 * <p>Each account row also carries a version number (optimistic locking) as a second guard. If any
 * code path ever changed an account without the lock, the commit would fail instead of silently
 * overwriting a balance. When a transaction does fail for a reason like that, or waits too long for a
 * lock, this class runs it again on fresh data a few times before giving up.
 *
 * <p>The retry lives here, outside the transaction, on purpose. A transaction that failed cannot be
 * continued. It has to be started again from the top.
 */
@Service
public class PostJournalEntry {

    private static final Logger log = LoggerFactory.getLogger(PostJournalEntry.class);
    private static final int MAX_ATTEMPTS = 6;

    private final JournalPoster poster;

    public PostJournalEntry(JournalPoster poster) {
        this.poster = poster;
    }

    /**
     * @param reference   the business reason, such as a payment id. One reference is posted once. Posting
     *                    it again with the same lines returns the first entry and moves no money
     * @param captureHold a hold to turn into this entry, or null. The hold is ended in the same
     *                    transaction, so the reserved money and the posted money never overlap or leave a gap
     */
    public record Command(String reference, List<Posting> postings, UUID captureHold) {}

    /** @param created false when the reference had been posted before and the first entry was returned */
    public record Result(JournalEntry entry, boolean created) {}

    public Result post(Command command) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return poster.post(command);
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                // Another transaction changed one of the accounts, or posted the same reference, first.
                last = e;
                log.debug("Posting {} collided on attempt {}. Trying again.", command.reference(), attempt);
                pause(attempt);
            }
        }
        throw new ConcurrentUpdateException("The accounts were too busy to post " + command.reference(), last);
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
