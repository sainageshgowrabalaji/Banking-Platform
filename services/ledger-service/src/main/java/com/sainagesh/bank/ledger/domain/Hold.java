package com.sainagesh.bank.ledger.domain;

import com.sainagesh.bank.ledger.domain.LedgerRuleException.Rule;
import com.sainagesh.bank.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Money reserved on an account for something that has not settled yet, such as a payment on its way.
 *
 * <p>A hold lowers the available balance at once, so the same money cannot be spent twice, but the
 * ledger balance stays the same until the payment settles. A hold ends in one of two ways. It is
 * captured, which means the real entry is posted, or it is released, which gives the money back.
 * A hold that nobody ends is released by itself when it expires.
 */
public final class Hold {

    private final UUID id;
    private final UUID accountId;
    private final Money amount;
    private final String reference;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Long version;

    private HoldStatus status;
    private Instant closedAt;

    private Hold(
            UUID id,
            UUID accountId,
            Money amount,
            String reference,
            HoldStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant closedAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A hold amount must be greater than zero");
        }
        this.closedAt = closedAt;
        this.version = version;
    }

    public static Hold place(UUID accountId, Money amount, String reference, Instant now, Duration lifetime) {
        return new Hold(
                UUID.randomUUID(), accountId, amount, reference, HoldStatus.ACTIVE, now, now.plus(lifetime), null, null);
    }

    public static Hold restore(
            UUID id,
            UUID accountId,
            Money amount,
            String reference,
            HoldStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant closedAt,
            Long version) {
        return new Hold(id, accountId, amount, reference, status, createdAt, expiresAt, closedAt, version);
    }

    public boolean isActive() {
        return status == HoldStatus.ACTIVE;
    }

    public void release(Instant now) {
        end(HoldStatus.RELEASED, now);
    }

    public void capture(Instant now) {
        end(HoldStatus.CAPTURED, now);
    }

    private void end(HoldStatus next, Instant now) {
        if (status != HoldStatus.ACTIVE) {
            throw new LedgerRuleException(
                    Rule.HOLD_NOT_ACTIVE, "Hold " + id + " is already " + status.name().toLowerCase());
        }
        status = next;
        closedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID accountId() {
        return accountId;
    }

    public Money amount() {
        return amount;
    }

    public String reference() {
        return reference;
    }

    public HoldStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public Long version() {
        return version;
    }
}
