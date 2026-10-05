package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.application.InvalidCursorException;
import com.sainagesh.bank.ledger.application.port.Journal;
import com.sainagesh.bank.ledger.domain.JournalEntry;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Keeps the journal in PostgreSQL through JPA. It only ever inserts and reads. */
@Repository
class JpaJournal implements Journal {

    private final EntityManager entities;
    private final JournalRows rows;

    JpaJournal(EntityManager entities, JournalRows rows) {
        this.entities = entities;
        this.rows = rows;
    }

    @Override
    public Optional<JournalEntry> findByReference(String reference) {
        return rows.findByReference(reference).map(JournalEntryEntity::toDomain);
    }

    @Override
    public void append(JournalEntry entry) {
        entities.persist(JournalEntryEntity.from(entry));
    }

    /**
     * Cursor paging. The cursor is the sequence number of the last entry of the page before, so the
     * next page starts right after it. Unlike page numbers, this stays correct while new entries arrive,
     * and it is as fast on page one thousand as on page one.
     */
    @Override
    public Page entriesOf(UUID accountId, int limit, String cursor) {
        long before = cursor == null ? Long.MAX_VALUE : decode(cursor);
        List<JournalEntryEntity> found = rows.pageOf(accountId, before, Limit.of(limit + 1));
        boolean more = found.size() > limit;
        List<JournalEntryEntity> page = more ? found.subList(0, limit) : found;
        String next = more ? encode(page.get(page.size() - 1).seq()) : null;
        return new Page(page.stream().map(JournalEntryEntity::toDomain).toList(), next);
    }

    private static String encode(long seq) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(seq).getBytes(StandardCharsets.UTF_8));
    }

    private static long decode(String cursor) {
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }

}
