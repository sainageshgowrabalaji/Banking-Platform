package com.sainagesh.bank.ledger.application;

import com.sainagesh.bank.ledger.application.port.Accounts;
import com.sainagesh.bank.ledger.domain.Account;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes, unfreezes and closes accounts. */
@Service
public class ChangeAccountStatus {

    private final Accounts accounts;
    private final Clock clock;

    public ChangeAccountStatus(Accounts accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account freeze(UUID accountId) {
        return change(accountId, Account::freeze);
    }

    @Transactional
    public Account unfreeze(UUID accountId) {
        return change(accountId, Account::unfreeze);
    }

    @Transactional
    public Account close(UUID accountId) {
        return change(accountId, account -> account.close(clock.instant()));
    }

    private Account change(UUID accountId, Consumer<Account> action) {
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> NotFoundException.account(accountId));
        action.accept(account);
        accounts.save(account);
        return account;
    }
}
