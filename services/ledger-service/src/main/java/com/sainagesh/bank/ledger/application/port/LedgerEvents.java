package com.sainagesh.bank.ledger.application.port;

import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.JournalEntry;

/** The facts the ledger announces to the rest of the platform. */
public interface LedgerEvents {

    void accountOpened(Account account);

    void entryPosted(JournalEntry entry);
}
