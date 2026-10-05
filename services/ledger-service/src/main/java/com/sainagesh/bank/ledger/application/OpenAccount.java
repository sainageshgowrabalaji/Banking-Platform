package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.idempotency.IdempotencyStore;
import com.sainagesh.bank.idempotency.StoredResult;
import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.application.port.LedgerEvents;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.AccountType;
import com.sainagesh.bank.web.IdempotencyKey;
import java.time.Clock;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens an account and announces it. */
@Service
public class OpenAccount {

    private static final String SCOPE = "accounts.open";

    private final Accounts accounts;
    private final LedgerEvents events;
    private final IdempotencyStore idempotency;
    private final Clock clock;

    public OpenAccount(Accounts accounts, LedgerEvents events, IdempotencyStore idempotency, Clock clock) {
        this.accounts = accounts;
        this.events = events;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public record Command(UUID customerId, AccountType type, String name, Currency currency) {}

    /**
     * The key, the account and its event are saved in one transaction. A retry with the same key finds
     * the stored result and returns the same account, so a client that never saw the first answer does
     * not end up with two accounts.
     */
    @Transactional
    public Account open(Command command, IdempotencyKey key, String requestHash) {
        Optional<StoredResult> before = idempotency.claim(SCOPE, key, requestHash);
        if (before.isPresent()) {
            UUID id = UUID.fromString(before.get().resourceId());
            return accounts.find(id).orElseThrow(() -> NotFoundException.account(id));
        }
        Account account =
                Account.open(command.customerId(), command.type(), command.name(), command.currency(), clock.instant());
        accounts.save(account);
        events.accountOpened(account);
        idempotency.complete(SCOPE, key, account.id().toString(), 201);
        return account;
    }
}
