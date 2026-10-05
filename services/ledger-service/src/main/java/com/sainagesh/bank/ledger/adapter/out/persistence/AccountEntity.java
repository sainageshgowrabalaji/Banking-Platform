package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.AccountStatus;
import com.sainagesh.bank.ledger.domain.AccountType;
import com.sainagesh.bank.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * The {@code account} table. This class only carries data between the database and the domain
 * {@link Account}. It holds no rules.
 */
@Entity
@Table(name = "account")
class AccountEntity {

    @Id
    private UUID id;

    private UUID customerId;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    private String name;
    private String currency;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private long ledgerBalanceMinor;
    private long heldMinor;
    private Instant openedAt;
    private Instant closedAt;

    /**
     * Optimistic locking. Every update adds one, and an update only succeeds if the version in the
     * database is still the one that was read. Two writers can never silently overwrite each other.
     */
    @Version
    private Long version;

    protected AccountEntity() {}

    static AccountEntity from(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.id = account.id();
        entity.customerId = account.customerId();
        entity.type = account.type();
        entity.name = account.name();
        entity.currency = account.currency().getCurrencyCode();
        entity.openedAt = account.openedAt();
        entity.copyChangingFields(account);
        return entity;
    }

    void copyChangingFields(Account account) {
        status = account.status();
        ledgerBalanceMinor = account.ledgerBalance().toMinorUnits();
        heldMinor = account.held().toMinorUnits();
        closedAt = account.closedAt();
    }

    Account toDomain() {
        return Account.restore(
                id,
                customerId,
                type,
                name,
                Currency.getInstance(currency),
                status,
                Money.ofMinorUnits(ledgerBalanceMinor, currency),
                Money.ofMinorUnits(heldMinor, currency),
                openedAt,
                closedAt,
                version);
    }

    UUID id() {
        return id;
    }

    Long version() {
        return version;
    }
}
