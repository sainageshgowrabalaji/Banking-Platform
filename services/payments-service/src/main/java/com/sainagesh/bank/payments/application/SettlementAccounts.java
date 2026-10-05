package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.payments.domain.Payment;
import java.util.UUID;

/**
 * Says which ledger account is credited when a payment settles.
 *
 * <p>A BOOK payment credits the receiver's own account. A payment that leaves the bank credits one of
 * the bank's settlement accounts, which stands for the money now owed to the network.
 */
public interface SettlementAccounts {

    UUID creditAccountFor(Payment payment);
}
