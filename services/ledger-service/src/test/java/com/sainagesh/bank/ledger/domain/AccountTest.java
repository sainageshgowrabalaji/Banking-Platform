package com.sainagesh.bank.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sainagesh.bank.ledger.domain.LedgerRuleException.Rule;
import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Each test is one rule of an account. No database and no Spring, so all of them run in milliseconds. */
class AccountTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");

    private static Money usd(String amount) {
        return Money.of(amount, "USD");
    }

    private static Account checking() {
        return Account.open(UUID.randomUUID(), AccountType.CHECKING, "Everyday", USD, NOW);
    }

    private static Account withBalance(String amount) {
        Account account = checking();
        account.post(Posting.credit(account.id().toString(), usd(amount)));
        return account;
    }

    private static Rule ruleOf(Runnable action) {
        return assertThrows(LedgerRuleException.class, action::run).rule();
    }

    @Test
    void aNewAccountIsActiveAndEmpty() {
        Account account = checking();

        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(usd("0.00"), account.ledgerBalance());
        assertEquals(usd("0.00"), account.available());
    }

    @Test
    void aCreditMakesACustomerBalanceGrowAndADebitMakesItShrink() {
        Account account = withBalance("100.00");

        account.post(Posting.debit(account.id().toString(), usd("30.25")));

        assertEquals(usd("69.75"), account.ledgerBalance());
    }

    @Test
    void anInternalAccountGrowsWithADebitBecauseItIsAnAsset() {
        Account cash = Account.open(null, AccountType.INTERNAL, "Cash", USD, NOW);

        cash.post(Posting.debit(cash.id().toString(), usd("500.00")));
        cash.post(Posting.credit(cash.id().toString(), usd("120.00")));

        assertEquals(usd("380.00"), cash.ledgerBalance());
    }

    @Test
    void aCustomerAccountCanNeverBeOverdrawn() {
        Account account = withBalance("50.00");

        assertEquals(Rule.INSUFFICIENT_FUNDS, ruleOf(() -> account.post(Posting.debit(account.id().toString(), usd("50.01")))));
        assertEquals(usd("50.00"), account.ledgerBalance());
    }

    @Test
    void anInternalAccountMayGoBelowZero() {
        Account settlement = Account.open(null, AccountType.INTERNAL, "Settlement", USD, NOW);

        settlement.post(Posting.credit(settlement.id().toString(), usd("75.00")));

        assertEquals(usd("-75.00"), settlement.ledgerBalance());
    }

    @Test
    void aHoldLowersWhatCanBeSpentButNotTheLedgerBalance() {
        Account account = withBalance("100.00");

        account.placeHold(usd("40.00"));

        assertEquals(usd("100.00"), account.ledgerBalance());
        assertEquals(usd("60.00"), account.available());
        assertEquals(Rule.INSUFFICIENT_FUNDS, ruleOf(() -> account.post(Posting.debit(account.id().toString(), usd("60.01")))));
    }

    @Test
    void heldMoneyCannotBeHeldTwice() {
        Account account = withBalance("100.00");
        account.placeHold(usd("70.00"));

        assertEquals(Rule.INSUFFICIENT_FUNDS, ruleOf(() -> account.placeHold(usd("30.01"))));
    }

    @Test
    void releasingAHoldGivesTheMoneyBack() {
        Account account = withBalance("100.00");
        account.placeHold(usd("40.00"));

        account.releaseHold(usd("40.00"));

        assertEquals(usd("100.00"), account.available());
    }

    @Test
    void moneyCanArriveInAFrozenAccountButCannotLeave() {
        Account account = withBalance("100.00");
        account.freeze();

        account.post(Posting.credit(account.id().toString(), usd("5.00")));

        assertEquals(usd("105.00"), account.ledgerBalance());
        assertEquals(Rule.ACCOUNT_FROZEN, ruleOf(() -> account.post(Posting.debit(account.id().toString(), usd("1.00")))));
        assertEquals(Rule.ACCOUNT_FROZEN, ruleOf(() -> account.placeHold(usd("1.00"))));
    }

    @Test
    void oneOfTheBanksOwnAccountsCannotBeFrozenOrClosed() {
        Account settlement = Account.open(null, AccountType.INTERNAL, "Settlement", USD, NOW);

        assertEquals(Rule.INVALID_STATUS_CHANGE, ruleOf(settlement::freeze));
        assertEquals(Rule.INVALID_STATUS_CHANGE, ruleOf(() -> settlement.close(NOW)));
        assertEquals(AccountStatus.ACTIVE, settlement.status());
    }

    @Test
    void anAccountMustBeEmptyBeforeItIsClosed() {
        Account account = withBalance("0.01");

        assertEquals(Rule.ACCOUNT_NOT_EMPTY, ruleOf(() -> account.close(NOW)));

        account.post(Posting.debit(account.id().toString(), usd("0.01")));
        account.close(NOW);

        assertEquals(AccountStatus.CLOSED, account.status());
        assertEquals(NOW, account.closedAt());
    }

    @Test
    void nothingMovesOnAClosedAccount() {
        Account account = checking();
        account.close(NOW);

        assertEquals(Rule.ACCOUNT_CLOSED, ruleOf(() -> account.post(Posting.credit(account.id().toString(), usd("1.00")))));
        assertEquals(Rule.INVALID_STATUS_CHANGE, ruleOf(() -> account.close(NOW)));
    }

    @Test
    void anAccountOnlyTakesItsOwnCurrency() {
        Account account = checking();

        assertEquals(
                Rule.CURRENCY_MISMATCH,
                ruleOf(() -> account.post(Posting.credit(account.id().toString(), Money.of("1.00", "EUR")))));
    }

    @Test
    void aCustomerAccountNeedsACustomer() {
        assertThrows(IllegalArgumentException.class, () -> Account.open(null, AccountType.SAVINGS, null, USD, NOW));
    }
}
