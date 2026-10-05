package com.sainagesh.bank.payments.adapter.out.messaging;

import com.sainagesh.bank.events.EventEnvelope;
import com.sainagesh.bank.events.Topics;
import com.sainagesh.bank.messaging.Outbox;
import com.sainagesh.bank.payments.application.port.PaymentEvents;
import com.sainagesh.bank.payments.domain.Payment;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes payment events through the outbox, and counts them. The event row is written in the
 * caller's transaction, and the relay sends it to Kafka afterwards.
 *
 * <p>The Kafka key is the payment id, so the events of one payment always arrive in the order they
 * happened.
 */
@Component
class OutboxPaymentEvents implements PaymentEvents {

    static final String SOURCE = "payments-service";

    private final Outbox outbox;
    private final MeterRegistry meters;

    OutboxPaymentEvents(Outbox outbox, MeterRegistry meters) {
        this.outbox = outbox;
        this.meters = meters;
    }

    record Amount(String amount, String currency) {}

    /** The payload from contracts/asyncapi/events-v1.yaml. */
    record PaymentData(
            String paymentId,
            String debtorAccountId,
            Amount amount,
            String rail,
            String status,
            String reasonCode,
            String creditorName) {}

    @Override
    public void accepted(Payment payment) {
        publish("bank.payments.accepted", payment);
    }

    @Override
    public void settled(Payment payment) {
        publish("bank.payments.settled", payment);
    }

    @Override
    public void rejected(Payment payment) {
        publish("bank.payments.rejected", payment);
    }

    @Override
    public void cancelled(Payment payment) {
        publish("bank.payments.cancelled", payment);
    }

    private void publish(String type, Payment payment) {
        PaymentData data = new PaymentData(
                payment.id().toString(),
                payment.debtorAccountId().toString(),
                new Amount(payment.amount().amount().toPlainString(), payment.amount().currency().getCurrencyCode()),
                payment.rail().name(),
                payment.status().name(),
                payment.reasonCode() == null ? null : payment.reasonCode().name(),
                payment.creditor().name());
        outbox.publish(Topics.PAYMENTS, EventEnvelope.of(type, SOURCE, payment.id().toString(), data));
        meters.counter("bank.payments", "rail", payment.rail().name(), "status", payment.status().name())
                .increment();
    }
}
