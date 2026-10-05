package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.application.port.Holds;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.LedgerRuleException;
import com.sainagesh.bank.ledger.domain.LedgerRuleException.Rule;
import com.sainagesh.bank.money.Money;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Reserves money on an account before a payment settles. */
@Service
public class PlaceHold {

    private static final int MAX_ATTEMPTS = 6;

    private final Accounts accounts;
    private final Holds holds;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PlaceHold(Accounts accounts, Holds holds, Clock clock, TransactionTemplate transaction) {
        this.accounts = accounts;
        this.holds = holds;
        this.clock = clock;
        this.transaction = transaction;
    }

    /**
     * @param reference why the money is reserved, such as {@code payment:1234}. Asking again with the same
     *                  reference returns the first hold and reserves nothing more
     */
    public record Command(UUID accountId, Money amount, String reference, Duration lifetime) {}

    public record Result(Hold hold, boolean created) {}

    public Result place(Command command) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> placeOnce(command));
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                last = e;
            }
        }
        throw new ConcurrentUpdateException("The account was too busy to place hold " + command.reference(), last);
    }

    private Result placeOnce(Command command) {
        // Lock the account before looking for the reference. Two requests for the same hold then
        // take turns, and the second one sees the hold the first one made.
        Account account = accounts.findForUpdate(command.accountId())
                .orElseThrow(() -> NotFoundException.account(command.accountId()));

        Optional<Hold> before = holds.findByReference(command.reference());
        if (before.isPresent()) {
            Hold hold = before.get();
            if (!hold.accountId().equals(command.accountId()) || !hold.amount().equals(command.amount())) {
                throw new DuplicateReferenceException(
                        "Reference " + command.reference() + " was already used for a different hold");
            }
            if (!hold.isActive() || !hold.expiresAt().isAfter(clock.instant())) {
                // Saying "here is your hold" would be a lie. The money is no longer reserved, or its
                // time has run out and the next housekeeping pass will give it back.
                throw new LedgerRuleException(
                        Rule.HOLD_NOT_ACTIVE,
                        "The hold for " + command.reference() + " is " + hold.status() + " and reserves nothing now");
            }
            return new Result(hold, false);
        }

        account.placeHold(command.amount());
        Hold hold = Hold.place(account.id(), command.amount(), command.reference(), clock.instant(), command.lifetime());
        accounts.save(account);
        holds.save(hold);
        return new Result(hold, true);
    }
}
