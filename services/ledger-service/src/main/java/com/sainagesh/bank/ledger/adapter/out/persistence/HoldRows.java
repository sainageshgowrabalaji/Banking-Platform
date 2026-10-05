package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.domain.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data writes the code for this interface. */
interface HoldRows extends Repository<HoldEntity, UUID> {

    Optional<HoldEntity> findByReference(String reference);

    @Query("select h.accountId from HoldEntity h where h.id = :id")
    Optional<UUID> findAccountIdById(UUID id);

    @Query("select h.id from HoldEntity h where h.status = :status and h.expiresAt < :now order by h.expiresAt")
    List<UUID> findIdsEndingBefore(HoldStatus status, Instant now, Limit limit);
}
