package com.sainagesh.bank.payments.domain;

/**
 * Why a payment was rejected or cancelled. These are ISO 20022 external status reason codes, the same
 * four-letter codes a real bank sends back.
 */
public enum ReasonCode {
    AC01("The account number is not correct"),
    AC04("The account is closed"),
    AC06("The account is blocked"),
    AM02("The amount is more than this rail allows"),
    AM04("There is not enough money in the account"),
    CUST("Cancelled at the customer's request"),
    MS03("No reason was given"),
    RR04("Stopped by a regulatory check");

    private final String meaning;

    ReasonCode(String meaning) {
        this.meaning = meaning;
    }

    public String meaning() {
        return meaning;
    }

    /** Reads a code from a message. A code this service does not know becomes MS03. */
    public static ReasonCode fromCode(String code) {
        for (ReasonCode known : values()) {
            if (known.name().equals(code)) {
                return known;
            }
        }
        return MS03;
    }
}
