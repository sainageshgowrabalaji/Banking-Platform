package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.application.port.Holds;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.HoldStatus;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Gives reserved money back, when a payment is rejected or cancelled, or when a hold runs out of time. */
@Service
public class ReleaseHold {

    private static final Logger log = LoggerFactory.getLogger(ReleaseHold.class);
    private static final int MAX_ATTEMPTS = 6;

    private final Accounts accounts;
    private final Holds holds;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ReleaseHold(Accounts accounts, Holds holds, Clock clock, TransactionTemplate transaction) {
        this.accounts = accounts;
        this.holds = holds;
        this.clock = clock;
        this.transaction = transaction;
    }

    /** Releasing a hold that is already released is fine and changes nothing, so a retry is always safe. */
    public Hold release(UUID holdId) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> releaseOnce(holdId));
            } catch (ConcurrencyFailureException e) {
                last = e;
            }
        }
        throw new ConcurrentUpdateException("The account was too busy to release hold " + holdId, last);
    }

    private Hold releaseOnce(UUID holdId) {
        // Find the account without loading the hold, lock the account, and only then read the hold.
        // Whoever holds the account lock sees the true state of every hold on it.
        UUID accountId = holds.accountOf(holdId).orElseThrow(() -> NotFoundException.hold(holdId));
        Account account =
                accounts.findForUpdate(accountId).orElseThrow(() -> NotFoundException.account(accountId));
        Hold hold = holds.find(holdId).orElseThrow(() -> NotFoundException.hold(holdId));
        if (hold.status() == HoldStatus.RELEASED) {
            return hold;
        }
        hold.release(clock.instant());
        account.releaseHold(hold.amount());
        accounts.save(account);
        holds.save(hold);
        return hold;
    }

    /** Releases holds whose time ran out. Returns how many were released. */
    public int releaseExpired(int limit) {
        int released = 0;
        for (UUID id : holds.findExpired(clock.instant(), limit)) {
            try {
                release(id);
                released++;
            } catch (RuntimeException e) {
                log.warn("Could not release expired hold {}. It will be tried again. {}", id, e.toString());
            }
        }
        return released;
    }
}
