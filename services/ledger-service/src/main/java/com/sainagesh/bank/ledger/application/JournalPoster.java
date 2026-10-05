package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.application.port.Holds;
import com.sainagesh.bank.ledger.application.port.Journal;
import com.sainagesh.bank.ledger.application.port.LedgerEvents;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.EntrySide;
import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.HoldStatus;
import com.sainagesh.bank.ledger.domain.JournalEntry;
import com.sainagesh.bank.ledger.domain.LedgerRuleException;
import com.sainagesh.bank.ledger.domain.LedgerRuleException.Rule;
import com.sainagesh.bank.ledger.domain.Posting;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One attempt at posting an entry, in one database transaction. {@link PostJournalEntry} runs it and
 * repeats it when it collides with another transaction.
 */
@Service
public class JournalPoster {

    private final Accounts accounts;
    private final Journal journal;
    private final Holds holds;
    private final LedgerEvents events;
    private final Clock clock;

    public JournalPoster(Accounts accounts, Journal journal, Holds holds, LedgerEvents events, Clock clock) {
        this.accounts = accounts;
        this.journal = journal;
        this.holds = holds;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PostJournalEntry.Result post(PostJournalEntry.Command command) {
        // 1. Build the entry. The constructor refuses it unless debits equal credits.
        JournalEntry entry = JournalEntry.of(command.reference(), command.postings());

        // 2. Load and lock every account the entry touches, in id order.
        Set<UUID> ids = new LinkedHashSet<>();
        for (Posting posting : entry.postings()) {
            ids.add(UUID.fromString(posting.accountId()));
        }
        Map<UUID, Account> touched = new LinkedHashMap<>();
        for (Account account : accounts.findAllForUpdate(ids)) {
            touched.put(account.id(), account);
        }

        // 3. A reference is posted once. A repeat returns the first entry and changes nothing. This
        //    is looked up after the locks are held, so two requests for the same reference take
        //    turns and the second one sees what the first one posted.
        Optional<JournalEntry> before = journal.findByReference(command.reference());
        if (before.isPresent()) {
            if (!before.get().postings().equals(command.postings())) {
                throw new DuplicateReferenceException(
                        "Reference " + command.reference() + " was already posted with different lines");
            }
            if (command.captureHold() != null) {
                // The caller believes this entry ended the hold. Only say "already done" if it did.
                Hold hold = holds.find(command.captureHold())
                        .orElseThrow(() -> NotFoundException.hold(command.captureHold()));
                if (hold.status() != HoldStatus.CAPTURED) {
                    throw new DuplicateReferenceException("Reference " + command.reference()
                            + " was already posted, but not as the entry that ends hold " + hold.id());
                }
            }
            return new PostJournalEntry.Result(before.get(), false);
        }
        for (UUID id : ids) {
            if (!touched.containsKey(id)) {
                throw NotFoundException.account(id);
            }
        }

        // 4. If this entry settles a hold, give the reserved money back first, so the debit below can use it.
        if (command.captureHold() != null) {
            Hold hold = holds.find(command.captureHold()).orElseThrow(() -> NotFoundException.hold(command.captureHold()));
            Account holder = touched.get(hold.accountId());
            if (holder == null) {
                throw new LedgerRuleException(
                        Rule.HOLD_NOT_ACTIVE, "Hold " + hold.id() + " is on an account this entry does not touch");
            }
            // The entry must take from that account exactly what the hold reserved. Otherwise a small
            // or unrelated entry could free a large hold without spending it.
            BigDecimal debited = BigDecimal.ZERO;
            for (Posting posting : entry.postings()) {
                if (posting.side() == EntrySide.DEBIT && posting.accountId().equals(hold.accountId().toString())) {
                    debited = debited.add(posting.amount().amount());
                }
            }
            if (debited.compareTo(hold.amount().amount()) != 0) {
                throw new LedgerRuleException(
                        Rule.HOLD_MISMATCH,
                        "Hold " + hold.id() + " reserves " + hold.amount() + " but this entry debits that account by "
                                + debited.toPlainString());
            }
            hold.capture(clock.instant());
            holder.releaseHold(hold.amount());
            holds.save(hold);
        }

        // 5. Apply each line. An account refuses a line that would break one of its rules.
        for (Posting posting : entry.postings()) {
            touched.get(UUID.fromString(posting.accountId())).post(posting);
        }

        // 6. Save the balances, the entry and the event together.
        touched.values().forEach(accounts::save);
        journal.append(entry);
        events.entryPosted(entry);
        return new PostJournalEntry.Result(entry, true);
    }
}
