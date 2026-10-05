package com.sainagesh.bank.ledger.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data writes the code for this interface. */
interface JournalRows extends Repository<JournalEntryEntity, UUID> {

    Optional<JournalEntryEntity> findByReference(String reference);

    @Query("""
            select e from JournalEntryEntity e
            where e.seq < :before
              and exists (select 1 from PostingEntity p where p.entry = e and p.accountId = :accountId)
            order by e.seq desc
            """)
    List<JournalEntryEntity> pageOf(UUID accountId, long before, Limit limit);
}
