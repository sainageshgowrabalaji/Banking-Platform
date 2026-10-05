package com.sainagesh.bank.ledger.application.port;

import com.sainagesh.bank.ledger.domain.Account;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where accounts are kept. The application says what it needs, and an adapter provides it. */
public interface Accounts {

    /** Reads an account. Use it when nothing will be changed. */
    Optional<Account> find(UUID id);

    /**
     * Reads an account and locks its row until the transaction ends. Anyone else who wants to change
     * the same account waits in line, so two changes can never be based on the same old balance.
     */
    Optional<Account> findForUpdate(UUID id);

    /**
     * Reads and locks several accounts, always in id order. Two transfers between the same two accounts
     * then ask for the locks in the same order, so they can never deadlock.
     */
    List<Account> findAllForUpdate(Collection<UUID> ids);

    /**
     * Stores a new account, or the changes to one that was loaded in this transaction. If another
     * transaction changed the same account in the meantime, the commit fails and nothing is saved.
     */
    void save(Account account);
}
