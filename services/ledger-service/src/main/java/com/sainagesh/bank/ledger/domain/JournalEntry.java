package com.sainagesh.bank.ledger.domain;

import com.sainagesh.bank.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One balanced movement of money. This is the heart of the ledger.
 *
 * <p>The rules are checked when the entry is built, so an unbalanced entry can never exist in memory, and
 * so can never reach the database.
 *
 * <ul>
 *   <li>An entry has at least two postings
 *   <li>All postings use one currency
 *   <li>The debits add up to exactly the credits
 * </ul>
 *
 * <p>Entries are never changed or deleted. A mistake is fixed by posting a new entry that reverses it,
 * which is how an auditor can replay every balance from the first day.
 *
 * @param id        unique id of the entry
 * @param reference the business reason, such as a payment id. One reference is posted at most once
 * @param postedAt  when the entry was posted
 * @param postings  the lines of the entry
 */
public record JournalEntry(UUID id, String reference, Instant postedAt, List<Posting> postings) {

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(postedAt, "postedAt");
        Objects.requireNonNull(postings, "postings");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new UnbalancedEntryException("A journal entry needs at least two postings");
        }
        Currency currency = postings.get(0).amount().currency();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Posting posting : postings) {
            if (!posting.amount().currency().equals(currency)) {
                throw new UnbalancedEntryException("All postings in one entry must use one currency");
            }
            if (posting.side() == EntrySide.DEBIT) {
                debits = debits.add(posting.amount().amount());
            } else {
                credits = credits.add(posting.amount().amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedEntryException(
                    "Debits " + debits.toPlainString() + " do not equal credits " + credits.toPlainString());
        }
    }

    public static JournalEntry of(String reference, List<Posting> postings) {
        return new JournalEntry(UUID.randomUUID(), reference, Instant.now(), postings);
    }

    /** Moves money between two customer accounts. The sender is debited and the receiver is credited. */
    public static JournalEntry transfer(String reference, String fromAccountId, String toAccountId, Money amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("A transfer needs two different accounts");
        }
        return of(reference, List.of(Posting.debit(fromAccountId, amount), Posting.credit(toAccountId, amount)));
    }

    /** The amount moved, which is the sum of the debits (and also of the credits). */
    public Money total() {
        Money sum = null;
        for (Posting posting : postings) {
            if (posting.side() == EntrySide.DEBIT) {
                sum = sum == null ? posting.amount() : sum.plus(posting.amount());
            }
        }
        return sum;
    }
}
