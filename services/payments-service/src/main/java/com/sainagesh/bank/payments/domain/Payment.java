package com.sainagesh.bank.payments.domain;

import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One payment and its state machine.
 *
 * <pre>
 *   RCVD --accept--> ACCP --send--> ACSP --settle--> ACSC
 *     |                |              |
 *     +----reject------+----reject----+--> RJCT
 *     +----cancel------+--> CANC
 * </pre>
 *
 * <p>Every move is a method, and a method refuses a move the current status does not allow. So an
 * impossible history, such as a settled payment that is later cancelled, cannot be built.
 *
 * <p>A payment that is rejected or cancelled after money was reserved still has work to do. The hold
 * must be released. {@link #holdReleasePending()} stays true until that has happened, which is how the
 * saga knows its undo step is still owed.
 */
public final class Payment {

    private final UUID id;
    private final UUID debtorAccountId;
    private final Party creditor;
    private final Money amount;
    private final Rail rail;
    private final String endToEndId;
    private final String remittanceInformation;
    private final Instant createdAt;
    private final Long version;

    private PaymentStatus status;
    private ReasonCode reasonCode;
    private String reasonDetail;
    private UUID holdId;
    private boolean holdReleasePending;
    private String railReference;
    private LocalDate settlementDate;
    private Instant settledAt;

    private Payment(
            UUID id,
            UUID debtorAccountId,
            Party creditor,
            Money amount,
            Rail rail,
            String endToEndId,
            String remittanceInformation,
            Instant createdAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.debtorAccountId = Objects.requireNonNull(debtorAccountId, "debtorAccountId");
        this.creditor = Objects.requireNonNull(creditor, "creditor");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.rail = Objects.requireNonNull(rail, "rail");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A payment amount must be greater than zero");
        }
        this.endToEndId = endToEndId;
        this.remittanceInformation = remittanceInformation;
        this.version = version;
    }

    /** A new payment, just received. */
    public static Payment receive(
            UUID debtorAccountId,
            Party creditor,
            Money amount,
            Rail rail,
            String endToEndId,
            String remittanceInformation,
            Instant now) {
        Payment payment = new Payment(
                UUID.randomUUID(), debtorAccountId, creditor, amount, rail, endToEndId, remittanceInformation, now, null);
        payment.status = PaymentStatus.RCVD;
        return payment;
    }

    /** Everything that changes after a payment is created, for rebuilding one from the database. */
    public record State(
            PaymentStatus status,
            ReasonCode reasonCode,
            String reasonDetail,
            UUID holdId,
            boolean holdReleasePending,
            String railReference,
            LocalDate settlementDate,
            Instant settledAt) {}

    public static Payment restore(
            UUID id,
            UUID debtorAccountId,
            Party creditor,
            Money amount,
            Rail rail,
            String endToEndId,
            String remittanceInformation,
            Instant createdAt,
            Long version,
            State state) {
        Payment payment =
                new Payment(id, debtorAccountId, creditor, amount, rail, endToEndId, remittanceInformation, createdAt, version);
        payment.status = Objects.requireNonNull(state.status(), "status");
        payment.reasonCode = state.reasonCode();
        payment.reasonDetail = state.reasonDetail();
        payment.holdId = state.holdId();
        payment.holdReleasePending = state.holdReleasePending();
        payment.railReference = state.railReference();
        payment.settlementDate = state.settlementDate();
        payment.settledAt = state.settledAt();
        return payment;
    }

    /** The money is reserved in the ledger. Recorded first, so it can always be found and given back. */
    public void fundsHeld(UUID holdId) {
        require(PaymentStatus.RCVD, "record a hold");
        this.holdId = Objects.requireNonNull(holdId, "holdId");
    }

    /** The money is reserved and every check passed. */
    public void accept() {
        require(PaymentStatus.RCVD, "be accepted");
        if (holdId == null) {
            throw new PaymentStateException("Payment " + id + " cannot be accepted before its money is reserved");
        }
        status = PaymentStatus.ACCP;
    }

    /**
     * About to be handed to its rail. This is saved before the rail is called. From here on the
     * customer can no longer cancel, because nobody can be sure the payment has not already left.
     */
    public void startSending() {
        require(PaymentStatus.ACCP, "be sent");
        status = PaymentStatus.ACSP;
    }

    /** The rail took the payment and gave its own reference for it. */
    public void sent(String railReference, LocalDate settlementDate) {
        require(PaymentStatus.ACSP, "be recorded as sent");
        this.railReference = Objects.requireNonNull(railReference, "railReference");
        this.settlementDate = settlementDate;
    }

    /** True once the rail has taken the payment. False while it is still being handed over. */
    public boolean onRail() {
        return railReference != null;
    }

    /** The money has moved. The hold was turned into the posted entry, so nothing is left to release. */
    public void settle(Instant now) {
        require(PaymentStatus.ACSP, "be settled");
        if (!onRail()) {
            throw new PaymentStateException("Payment " + id + " cannot be settled before its rail has taken it");
        }
        status = PaymentStatus.ACSC;
        settledAt = now;
    }

    /** Refused by a check, by the ledger or by the rail. */
    public void reject(ReasonCode reason, String detail) {
        if (status.isFinal()) {
            throw new PaymentStateException("Payment " + id + " is already " + status + " and cannot be rejected");
        }
        end(PaymentStatus.RJCT, reason, detail);
    }

    /** Stopped by the customer. Only possible before the payment is on its rail. */
    public void cancel() {
        if (status != PaymentStatus.RCVD && status != PaymentStatus.ACCP) {
            throw new PaymentStateException(
                    "Payment " + id + " is " + status + ". Only a payment that has not been sent yet can be cancelled");
        }
        // A payment still at RCVD may have a hold nobody recorded. The ledger can have reserved the
        // money in a call whose answer never arrived. So the undo step is owed even with no hold id.
        boolean holdMayExist = holdId != null || status == PaymentStatus.RCVD;
        end(PaymentStatus.CANC, ReasonCode.CUST, ReasonCode.CUST.meaning());
        holdReleasePending = holdMayExist;
    }

    private void end(PaymentStatus next, ReasonCode reason, String detail) {
        status = next;
        reasonCode = reason;
        reasonDetail = detail;
        holdReleasePending = holdId != null;
    }

    /** The undo step is done. The reserved money is back with the customer. */
    public void holdReleased() {
        holdReleasePending = false;
    }

    /** True while the saga still has something to do for this payment. */
    public boolean needsWork() {
        return !status.isFinal() || holdReleasePending;
    }

    private void require(PaymentStatus expected, String move) {
        if (status != expected) {
            throw new PaymentStateException("Payment " + id + " is " + status + " and cannot " + move);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID debtorAccountId() {
        return debtorAccountId;
    }

    public Party creditor() {
        return creditor;
    }

    public Money amount() {
        return amount;
    }

    public Rail rail() {
        return rail;
    }

    public String endToEndId() {
        return endToEndId;
    }

    public String remittanceInformation() {
        return remittanceInformation;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long version() {
        return version;
    }

    public PaymentStatus status() {
        return status;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public String reasonDetail() {
        return reasonDetail;
    }

    public UUID holdId() {
        return holdId;
    }

    public boolean holdReleasePending() {
        return holdReleasePending;
    }

    public String railReference() {
        return railReference;
    }

    public LocalDate settlementDate() {
        return settlementDate;
    }

    public Instant settledAt() {
        return settledAt;
    }

    /** The reference this payment uses in the ledger, for its hold and for its journal entry. */
    public String ledgerReference() {
        return "payment:" + id;
    }
}
