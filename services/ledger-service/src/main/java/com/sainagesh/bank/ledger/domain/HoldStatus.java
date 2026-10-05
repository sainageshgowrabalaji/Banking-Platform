package com.sainagesh.bank.ledger.domain;

/** ACTIVE reserves the money. RELEASED gave it back. CAPTURED turned it into a posted entry. */
public enum HoldStatus {
    ACTIVE,
    RELEASED,
    CAPTURED
}
