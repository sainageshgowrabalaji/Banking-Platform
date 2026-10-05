package com.sainagesh.bank.payments.domain;

/**
 * Where a payment is in its life. The codes are the ISO 20022 payment status codes, so they mean the
 * same thing to any bank.
 *
 * <ul>
 *   <li>RCVD. Received. Nothing has been checked yet
 *   <li>ACCP. Accepted. The money is reserved and the checks passed
 *   <li>ACSP. In process. Handed to the rail, waiting for settlement
 *   <li>ACSC. Settled. The money has moved. Final
 *   <li>RJCT. Rejected, with a reason code. Final
 *   <li>CANC. Cancelled by the customer before it was sent. Final
 * </ul>
 */
public enum PaymentStatus {
    RCVD(false),
    ACCP(false),
    ACSP(false),
    ACSC(true),
    RJCT(true),
    CANC(true);

    private final boolean isFinal;

    PaymentStatus(boolean isFinal) {
        this.isFinal = isFinal;
    }

    /** A final status never changes again. */
    public boolean isFinal() {
        return isFinal;
    }
}
