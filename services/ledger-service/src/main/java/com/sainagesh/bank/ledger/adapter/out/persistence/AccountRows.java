package com.sainagesh.bank.ledger.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data writes the code for this interface. Only the adapters in this package use it. */
interface AccountRows extends Repository<AccountEntity, UUID> {

    /** "select ... order by id for update". The rows stay locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id in :ids order by a.id")
    List<AccountEntity> lockAllInIdOrder(Collection<UUID> ids);
}
