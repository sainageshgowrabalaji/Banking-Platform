package com.sainagesh.bank.ledger.domain;

/**
 * The life of an account.
 *
 * <ul>
 *   <li>PENDING. Opened but not yet cleared by the customer checks. No money may move
 *   <li>ACTIVE. Normal use
 *   <li>FROZEN. Money may arrive but may not leave. Used when fraud is suspected or a court orders it
 *   <li>CLOSED. The end. A closed account is never opened again
 * </ul>
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    FROZEN,
    CLOSED
}
