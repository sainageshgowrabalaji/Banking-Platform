package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.HoldStatus;
import com.sainagesh.bank.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The {@code hold} table. */
@Entity
@Table(name = "hold")
class HoldEntity {

    @Id
    private UUID id;

    private UUID accountId;
    private long amountMinor;
    private String currency;
    private String reference;

    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant closedAt;

    @Version
    private Long version;

    protected HoldEntity() {}

    static HoldEntity from(Hold hold) {
        HoldEntity entity = new HoldEntity();
        entity.id = hold.id();
        entity.accountId = hold.accountId();
        entity.amountMinor = hold.amount().toMinorUnits();
        entity.currency = hold.amount().currency().getCurrencyCode();
        entity.reference = hold.reference();
        entity.createdAt = hold.createdAt();
        entity.expiresAt = hold.expiresAt();
        entity.copyChangingFields(hold);
        return entity;
    }

    void copyChangingFields(Hold hold) {
        status = hold.status();
        closedAt = hold.closedAt();
    }

    Hold toDomain() {
        return Hold.restore(
                id, accountId, Money.ofMinorUnits(amountMinor, currency), reference, status, createdAt, expiresAt, closedAt, version);
    }

    Long version() {
        return version;
    }
}
