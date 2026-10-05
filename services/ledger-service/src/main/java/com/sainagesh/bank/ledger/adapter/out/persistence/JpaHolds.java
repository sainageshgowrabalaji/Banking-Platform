package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.application.port.Holds;
import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.HoldStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Keeps holds in PostgreSQL through JPA. */
@Repository
class JpaHolds implements Holds {

    private final EntityManager entities;
    private final HoldRows rows;

    JpaHolds(EntityManager entities, HoldRows rows) {
        this.entities = entities;
        this.rows = rows;
    }

    @Override
    public Optional<Hold> find(UUID id) {
        return Optional.ofNullable(entities.find(HoldEntity.class, id)).map(HoldEntity::toDomain);
    }

    @Override
    public Optional<Hold> findByReference(String reference) {
        return rows.findByReference(reference).map(HoldEntity::toDomain);
    }

    @Override
    public Optional<UUID> accountOf(UUID holdId) {
        return rows.findAccountIdById(holdId);
    }

    @Override
    public void save(Hold hold) {
        if (hold.version() == null) {
            entities.persist(HoldEntity.from(hold));
            return;
        }
        HoldEntity entity = entities.find(HoldEntity.class, hold.id());
        if (entity == null || !hold.version().equals(entity.version())) {
            throw new ObjectOptimisticLockingFailureException(HoldEntity.class, hold.id());
        }
        entity.copyChangingFields(hold);
    }

    @Override
    public List<UUID> findExpired(Instant now, int limit) {
        return rows.findIdsEndingBefore(HoldStatus.ACTIVE, now, Limit.of(limit));
    }
}
