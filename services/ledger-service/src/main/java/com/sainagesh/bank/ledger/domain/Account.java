package com.sainagesh.bank.ledger.domain;

import com.sainagesh.bank.ledger.domain.LedgerRuleException.Rule;
import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * One account and the rules that protect it.
 *
 * <p>The account keeps a running balance so a balance check does not have to add up every entry. The
 * running balance is changed only by {@link #post}, in the same database transaction that stores the
 * entry, so it always equals the sum of the entries. A database check and a test both prove it.
 *
 * <p>Two numbers matter to a customer.
 *
 * <ul>
 *   <li>The <b>ledger balance</b> counts every posted entry
 *   <li>The <b>available balance</b> is the ledger balance minus the holds. It is what can be spent
 * </ul>
 */
public final class Account {

    private final UUID id;
    private final UUID customerId;
    private final AccountType type;
    private final String name;
    private final Currency currency;
    private final Instant openedAt;
    private final Long version;

    private AccountStatus status;
    private Money ledgerBalance;
    private Money held;
    private Instant closedAt;

    private Account(
            UUID id,
            UUID customerId,
            AccountType type,
            String name,
            Currency currency,
            AccountStatus status,
            Money ledgerBalance,
            Money held,
            Instant openedAt,
            Instant closedAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = Objects.requireNonNull(status, "status");
        this.ledgerBalance = Objects.requireNonNull(ledgerBalance, "ledgerBalance");
        this.held = Objects.requireNonNull(held, "held");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        if (type.customerOwned() && customerId == null) {
            throw new IllegalArgumentException("A customer account needs a customer");
        }
        this.customerId = customerId;
        this.name = name;
        this.closedAt = closedAt;
        this.version = version;
    }

    /** Opens a new account with a zero balance. */
    public static Account open(UUID customerId, AccountType type, String name, Currency currency, Instant now) {
        Money zero = Money.zero(currency.getCurrencyCode());
        return new Account(
                UUID.randomUUID(), customerId, type, name, currency, AccountStatus.ACTIVE, zero, zero, now, null, null);
    }

    /** Rebuilds an account that was loaded from the database. */
    public static Account restore(
            UUID id,
            UUID customerId,
            AccountType type,
            String name,
            Currency currency,
            AccountStatus status,
            Money ledgerBalance,
            Money held,
            Instant openedAt,
            Instant closedAt,
            Long version) {
        return new Account(id, customerId, type, name, currency, status, ledgerBalance, held, openedAt, closedAt, version);
    }

    /** What can be spent right now. */
    public Money available() {
        return ledgerBalance.minus(held);
    }

    /**
     * Applies one line of a journal entry to this account.
     *
     * <p>A posting on the account's normal side makes the balance grow, and one on the other side makes
     * it shrink. Money leaving a customer account is refused when the account is frozen, and when it
     * would take the available balance below zero.
     */
    public void post(Posting posting) {
        requireSameCurrency(posting.amount());
        if (status == AccountStatus.CLOSED) {
            throw new LedgerRuleException(Rule.ACCOUNT_CLOSED, "Account " + id + " is closed");
        }
        if (status == AccountStatus.PENDING) {
            throw new LedgerRuleException(Rule.ACCOUNT_NOT_ACTIVE, "Account " + id + " is not active yet");
        }
        boolean grows = posting.side() == type.normalSide();
        if (grows) {
            ledgerBalance = ledgerBalance.plus(posting.amount());
            return;
        }
        if (status == AccountStatus.FROZEN) {
            throw new LedgerRuleException(Rule.ACCOUNT_FROZEN, "Account " + id + " is frozen. Money cannot leave it");
        }
        Money next = ledgerBalance.minus(posting.amount());
        if (type.customerOwned() && next.minus(held).isNegative()) {
            throw new LedgerRuleException(
                    Rule.INSUFFICIENT_FUNDS,
                    "Account " + id + " has " + available() + " available, which is less than " + posting.amount());
        }
        ledgerBalance = next;
    }

    /** Reserves money for a payment that has not settled yet. The ledger balance does not change. */
    public void placeHold(Money amount) {
        requireSameCurrency(amount);
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A hold amount must be greater than zero");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new LedgerRuleException(
                    status == AccountStatus.FROZEN ? Rule.ACCOUNT_FROZEN
                            : status == AccountStatus.CLOSED ? Rule.ACCOUNT_CLOSED : Rule.ACCOUNT_NOT_ACTIVE,
                    "Account " + id + " is " + status.name().toLowerCase() + ". Money cannot be reserved on it");
        }
        if (type.customerOwned() && available().compareTo(amount) < 0) {
            throw new LedgerRuleException(
                    Rule.INSUFFICIENT_FUNDS,
                    "Account " + id + " has " + available() + " available, which is less than " + amount);
        }
        held = held.plus(amount);
    }

    /** Gives reserved money back, when a hold is released or turned into a posted entry. */
    public void releaseHold(Money amount) {
        requireSameCurrency(amount);
        if (held.compareTo(amount) < 0) {
            throw new IllegalStateException("Account " + id + " holds " + held + ", cannot release " + amount);
        }
        held = held.minus(amount);
    }

    public void freeze() {
        requireCustomerOwned();
        if (status != AccountStatus.ACTIVE) {
            throw new LedgerRuleException(Rule.INVALID_STATUS_CHANGE, "Only an active account can be frozen");
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new LedgerRuleException(Rule.INVALID_STATUS_CHANGE, "Only a frozen account can be unfrozen");
        }
        status = AccountStatus.ACTIVE;
    }

    /** Closes the account. It must be empty first, so no money is ever lost in a closed account. */
    public void close(Instant now) {
        requireCustomerOwned();
        if (status == AccountStatus.CLOSED) {
            throw new LedgerRuleException(Rule.INVALID_STATUS_CHANGE, "The account is already closed");
        }
        if (!ledgerBalance.isZero() || !held.isZero()) {
            throw new LedgerRuleException(
                    Rule.ACCOUNT_NOT_EMPTY, "Account " + id + " still has a balance or a hold. Empty it before closing");
        }
        status = AccountStatus.CLOSED;
        closedAt = now;
    }

    /**
     * The bank's own accounts carry every payment that leaves on a rail. Freezing or closing one would
     * stop them all, and a closed account cannot be opened again. So their status never changes here.
     */
    private void requireCustomerOwned() {
        if (!type.customerOwned()) {
            throw new LedgerRuleException(
                    Rule.INVALID_STATUS_CHANGE, "One of the bank's own accounts cannot be frozen or closed");
        }
    }

    private void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(currency)) {
            throw new LedgerRuleException(
                    Rule.CURRENCY_MISMATCH,
                    "Account " + id + " is in " + currency.getCurrencyCode() + ", not "
                            + amount.currency().getCurrencyCode());
        }
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public AccountType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Currency currency() {
        return currency;
    }

    public AccountStatus status() {
        return status;
    }

    public Money ledgerBalance() {
        return ledgerBalance;
    }

    public Money held() {
        return held;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    /** The version the row had when it was loaded. Null for an account that is not stored yet. */
    public Long version() {
        return version;
    }
}
