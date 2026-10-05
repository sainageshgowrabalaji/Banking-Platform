package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.config.LedgerProperties;
import com.sainagesh.bank.ledger.domain.Account;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Keeps accounts in PostgreSQL through JPA. */
@Repository
class JpaAccounts implements Accounts {

    private final EntityManager entities;
    private final AccountRows rows;
    private final String lockTimeoutMillis;

    JpaAccounts(EntityManager entities, AccountRows rows, LedgerProperties properties) {
        this.entities = entities;
        this.rows = rows;
        this.lockTimeoutMillis = Long.toString(properties.lockTimeout().toMillis());
    }

    @Override
    public Optional<Account> find(UUID id) {
        return Optional.ofNullable(entities.find(AccountEntity.class, id)).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> findForUpdate(UUID id) {
        return findAllForUpdate(List.of(id)).stream().findFirst();
    }

    @Override
    public List<Account> findAllForUpdate(Collection<UUID> ids) {
        // Never wait for a lock without a limit. The setting lasts until this transaction ends. When
        // the time is up PostgreSQL fails the statement, and the caller starts the attempt again.
        entities.createNativeQuery("select set_config('lock_timeout', :millis, true)")
                .setParameter("millis", lockTimeoutMillis)
                .getSingleResult();
        return rows.lockAllInIdOrder(ids).stream().map(AccountEntity::toDomain).toList();
    }

    @Override
    public void save(Account account) {
        if (account.version() == null) {
            entities.persist(AccountEntity.from(account));
            return;
        }
        // The row was loaded earlier in this transaction, so this find costs nothing.
        AccountEntity entity = entities.find(AccountEntity.class, account.id());
        if (entity == null || !account.version().equals(entity.version())) {
            throw new ObjectOptimisticLockingFailureException(AccountEntity.class, account.id());
        }
        // Hibernate writes the changes at commit, with "where version = the one that was read".
        entity.copyChangingFields(account);
    }
}
