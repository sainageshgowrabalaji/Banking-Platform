package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.application.port.Holds;
import com.sainagesh.bank.ledger.application.port.Journal;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.Hold;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side. Nothing here changes anything. */
@Service
@Transactional(readOnly = true)
public class AccountQueries {

    private final Accounts accounts;
    private final Journal journal;
    private final Holds holds;

    public AccountQueries(Accounts accounts, Journal journal, Holds holds) {
        this.accounts = accounts;
        this.journal = journal;
        this.holds = holds;
    }

    public Account account(UUID accountId) {
        return accounts.find(accountId).orElseThrow(() -> NotFoundException.account(accountId));
    }

    public Journal.Page entries(UUID accountId, int limit, String cursor) {
        account(accountId);
        return journal.entriesOf(accountId, limit, cursor);
    }

    public Hold hold(UUID holdId) {
        return holds.find(holdId).orElseThrow(() -> NotFoundException.hold(holdId));
    }
}
