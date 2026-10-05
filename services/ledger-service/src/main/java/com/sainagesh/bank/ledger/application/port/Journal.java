package com.sainagesh.bank.ledger.application.port;

import com.sainagesh.bank.ledger.domain.JournalEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The journal. Entries are added and read, never changed. */
public interface Journal {

    Optional<JournalEntry> findByReference(String reference);

    void append(JournalEntry entry);

    /**
     * One page of the entries that touched an account, newest first.
     *
     * @param cursor the {@code nextCursor} of the page before, or null for the first page
     */
    Page entriesOf(UUID accountId, int limit, String cursor);

    /** @param nextCursor null on the last page */
    record Page(List<JournalEntry> items, String nextCursor) {}
}
