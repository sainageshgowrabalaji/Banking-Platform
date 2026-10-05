package com.sainagesh.bank.ledger.adapter.out.persistence;

import com.sainagesh.bank.ledger.domain.EntrySide;
import com.sainagesh.bank.ledger.domain.Posting;
import com.sainagesh.bank.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** The {@code posting} table. One line of a journal entry. */
@Entity
@Table(name = "posting")
class PostingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntryEntity entry;

    private int lineNo;
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    private EntrySide side;

    private long amountMinor;
    private String currency;

    protected PostingEntity() {}

    static PostingEntity from(JournalEntryEntity entry, int lineNo, Posting posting) {
        PostingEntity entity = new PostingEntity();
        entity.entry = entry;
        entity.lineNo = lineNo;
        entity.accountId = UUID.fromString(posting.accountId());
        entity.side = posting.side();
        entity.amountMinor = posting.amount().toMinorUnits();
        entity.currency = posting.amount().currency().getCurrencyCode();
        return entity;
    }

    Posting toDomain() {
        return new Posting(accountId.toString(), side, Money.ofMinorUnits(amountMinor, currency));
    }
}
