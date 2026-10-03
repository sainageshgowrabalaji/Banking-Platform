package com.sainagesh.bank.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sainagesh.bank.money.Money;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Money TEN = Money.of("10.00", "USD");

    @Test
    void aTransferDebitsTheSenderAndCreditsTheReceiver() {
        JournalEntry entry = JournalEntry.transfer("pay-1", "acc-alice", "acc-bob", TEN);

        assertEquals(List.of(Posting.debit("acc-alice", TEN), Posting.credit("acc-bob", TEN)), entry.postings());
        assertEquals(TEN, entry.total());
    }

    @Test
    void anEntryCanSplitOneDebitAcrossSeveralCredits() {
        JournalEntry entry = JournalEntry.of("pay-2", List.of(
                Posting.debit("acc-alice", TEN),
                Posting.credit("acc-bob", Money.of("9.50", "USD")),
                Posting.credit("fees-income", Money.of("0.50", "USD"))));

        assertEquals(TEN, entry.total());
    }

    @Test
    void debitsMustEqualCredits() {
        assertThrows(UnbalancedEntryException.class, () -> JournalEntry.of("pay-3", List.of(
                Posting.debit("acc-alice", TEN),
                Posting.credit("acc-bob", Money.of("9.99", "USD")))));
    }

    @Test
    void oneSidedEntriesAreRejected() {
        assertThrows(UnbalancedEntryException.class,
                () -> JournalEntry.of("pay-4", List.of(Posting.debit("acc-alice", TEN))));
        assertThrows(UnbalancedEntryException.class, () -> JournalEntry.of("pay-5", List.of(
                Posting.debit("acc-alice", TEN),
                Posting.debit("acc-bob", TEN))));
    }

    @Test
    void oneEntryUsesOneCurrency() {
        assertThrows(UnbalancedEntryException.class, () -> JournalEntry.of("pay-6", List.of(
                Posting.debit("acc-alice", TEN),
                Posting.credit("acc-bob", Money.of("10.00", "EUR")))));
    }

    @Test
    void aPostingAmountIsAlwaysGreaterThanZero() {
        assertThrows(IllegalArgumentException.class, () -> Posting.debit("acc-alice", Money.zero("USD")));
        assertThrows(IllegalArgumentException.class, () -> Posting.credit("acc-alice", TEN.negate()));
    }

    @Test
    void aTransferNeedsTwoDifferentAccounts() {
        assertThrows(IllegalArgumentException.class, () -> JournalEntry.transfer("pay-7", "acc-alice", "acc-alice", TEN));
    }

    @Test
    void aPostedEntryCannotBeChanged() {
        List<Posting> lines = new ArrayList<>(List.of(Posting.debit("acc-alice", TEN), Posting.credit("acc-bob", TEN)));
        JournalEntry entry = JournalEntry.of("pay-8", lines);

        lines.clear();

        assertEquals(2, entry.postings().size());
        assertThrows(UnsupportedOperationException.class, () -> entry.postings().clear());
    }
}
