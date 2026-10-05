package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.idempotency.IdempotencyStore;
import com.sainagesh.bank.idempotency.StoredResult;
import com.sainagesh.bank.money.Money;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.Party;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.web.IdempotencyKey;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Takes in a new payment. */
@Service
public class CreatePayment {

    private static final Logger log = LoggerFactory.getLogger(CreatePayment.class);
    private static final String SCOPE = "payments.create";

    private final PaymentStore payments;
    private final IdempotencyStore idempotency;
    private final PaymentSaga saga;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CreatePayment(
            PaymentStore payments,
            IdempotencyStore idempotency,
            PaymentSaga saga,
            TransactionTemplate transaction,
            Clock clock) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.saga = saga;
        this.transaction = transaction;
        this.clock = clock;
    }

    public record Command(
            UUID debtorAccountId, Party creditor, Money amount, Rail rail, String endToEndId, String remittanceInformation) {}

    private record Stored(UUID paymentId, boolean isNew) {}

    /**
     * Stores the payment, then starts its saga.
     *
     * <p>The payment and its Idempotency-Key are saved in one transaction. A retry with the same key
     * finds the stored payment and returns it as it is now, without starting anything twice.
     *
     * <p>The first saga steps then run right here, so the caller usually gets an answer that already
     * says accepted, settled or rejected. If the ledger is slow or down, that is fine too. The payment
     * is stored, the caller gets it back as received, and the background worker finishes the job.
     */
    public Payment create(Command command, IdempotencyKey key, String requestHash) {
        validate(command);
        Stored stored = transaction.execute(status -> {
            Optional<StoredResult> before = idempotency.claim(SCOPE, key, requestHash);
            if (before.isPresent()) {
                return new Stored(UUID.fromString(before.get().resourceId()), false);
            }
            Payment payment = Payment.receive(
                    command.debtorAccountId(),
                    command.creditor(),
                    command.amount(),
                    command.rail(),
                    command.endToEndId(),
                    command.remittanceInformation(),
                    clock.instant());
            payments.insert(payment, clock.instant());
            idempotency.complete(SCOPE, key, payment.id().toString(), 202);
            return new Stored(payment.id(), true);
        });
        if (stored.isNew()) {
            try {
                saga.advance(stored.paymentId());
            } catch (RuntimeException e) {
                log.warn("Payment {} is stored. The worker will carry it on. {}", stored.paymentId(), e.toString());
            }
        }
        return payments.find(stored.paymentId()).orElseThrow(() -> new PaymentNotFoundException(stored.paymentId()));
    }

    /** Text that travels on to other banks must be short enough and free of control characters. */
    private static void checkText(String what, String text, int maxLength) {
        if (text == null) {
            return;
        }
        if (text.length() > maxLength) {
            throw new InvalidPaymentException(what + " must be at most " + maxLength + " characters");
        }
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidPaymentException(what + " must not contain control characters");
        }
        // These fields travel in ISO 20022 messages, which are XML. A character XML does not allow
        // would make a message that no bank can read, long after this request was accepted.
        if (!text.codePoints().allMatch(CreatePayment::allowedInXml)) {
            throw new InvalidPaymentException(what + " contains a character that cannot be sent in a payment message");
        }
    }

    /** The characters XML 1.0 allows. It leaves out lone surrogates and the two "not a character" values. */
    static boolean allowedInXml(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    /** Checks that need nothing but the request itself. Everything else is checked by the saga. */
    private static void validate(Command command) {
        if (!command.amount().isPositive()) {
            throw new InvalidPaymentException("The amount must be greater than zero");
        }
        Party creditor = command.creditor();
        // The same limits for a payment that came as JSON and one that came as an ISO 20022 message.
        checkText("The creditor name", creditor.name(), 140);
        checkText("The creditor accountNumber", creditor.accountNumber(), 64);
        checkText("The endToEndId", command.endToEndId(), 35);
        checkText("The remittanceInformation", command.remittanceInformation(), 140);
        if (command.rail() == Rail.BOOK) {
            UUID creditorAccount;
            try {
                creditorAccount = UUID.fromString(creditor.accountNumber());
            } catch (IllegalArgumentException e) {
                throw new InvalidPaymentException(
                        "For a BOOK payment the creditor accountNumber must be the id of an account in this bank");
            }
            if (creditorAccount.equals(command.debtorAccountId())) {
                throw new InvalidPaymentException("A payment needs two different accounts");
            }
            return;
        }
        if (creditor.routingNumber() == null || !creditor.routingNumber().matches("[0-9]{9}")) {
            throw new InvalidPaymentException(
                    "For an " + command.rail() + " payment the creditor routingNumber must be nine digits");
        }
        if (!"USD".equals(command.amount().currency().getCurrencyCode())) {
            throw new InvalidPaymentException("The " + command.rail() + " rail only carries USD");
        }
    }
}
