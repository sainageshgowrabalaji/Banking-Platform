package com.sainagesh.bank.ledger.adapter.out.messaging;

import com.sainagesh.bank.events.EventEnvelope;
import com.sainagesh.bank.events.Topics;
import com.sainagesh.bank.ledger.application.port.LedgerEvents;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.JournalEntry;
import com.sainagesh.bank.messaging.Outbox;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Publishes the ledger's events through the outbox. The event row is written in the caller's
 * transaction, and the relay sends it to Kafka afterwards.
 */
@Component
class OutboxLedgerEvents implements LedgerEvents {

    static final String SOURCE = "ledger-service";

    private final Outbox outbox;

    OutboxLedgerEvents(Outbox outbox) {
        this.outbox = outbox;
    }

    record Amount(String amount, String currency) {}

    record AccountOpened(String accountId, String customerId, String type, String currency, String status) {}

    record Line(String accountId, String side, Amount amount) {}

    record EntryPosted(String entryId, String reference, List<Line> postings) {}

    @Override
    public void accountOpened(Account account) {
        AccountOpened data = new AccountOpened(
                account.id().toString(),
                account.customerId() == null ? null : account.customerId().toString(),
                account.type().name(),
                account.currency().getCurrencyCode(),
                account.status().name());
        outbox.publish(
                Topics.ACCOUNTS, EventEnvelope.of("bank.accounts.opened", SOURCE, account.id().toString(), data));
    }

    @Override
    public void entryPosted(JournalEntry entry) {
        List<Line> lines = entry.postings().stream()
                .map(p -> new Line(
                        p.accountId(),
                        p.side().name(),
                        new Amount(p.amount().amount().toPlainString(), p.amount().currency().getCurrencyCode())))
                .toList();
        EntryPosted data = new EntryPosted(entry.id().toString(), entry.reference(), lines);
        // The key is the entry id. Readers that need per-account order use the postings inside.
        outbox.publish(Topics.LEDGER, EventEnvelope.of("bank.ledger.entry-posted", SOURCE, entry.id().toString(), data));
    }
}
