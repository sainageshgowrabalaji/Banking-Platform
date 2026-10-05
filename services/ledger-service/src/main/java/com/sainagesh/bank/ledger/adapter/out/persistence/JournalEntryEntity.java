package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.domain.JournalEntry;
import com.sainagesh.bank.ledger.domain.Posting;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/** The {@code journal_entry} table. Rows are inserted and read. The database refuses updates and deletes. */
@Entity
@Table(name = "journal_entry")
class JournalEntryEntity {

    @Id
    private UUID id;

    /** Given by the database in posting order. It is the stable order for paging. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    private String reference;
    private Instant postedAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("lineNo")
    @BatchSize(size = 200)
    private List<PostingEntity> postings = new ArrayList<>();

    protected JournalEntryEntity() {}

    static JournalEntryEntity from(JournalEntry entry) {
        JournalEntryEntity entity = new JournalEntryEntity();
        entity.id = entry.id();
        entity.reference = entry.reference();
        entity.postedAt = entry.postedAt();
        int line = 1;
        for (Posting posting : entry.postings()) {
            entity.postings.add(PostingEntity.from(entity, line++, posting));
        }
        return entity;
    }

    JournalEntry toDomain() {
        return new JournalEntry(id, reference, postedAt, postings.stream().map(PostingEntity::toDomain).toList());
    }

    Long seq() {
        return seq;
    }
}
