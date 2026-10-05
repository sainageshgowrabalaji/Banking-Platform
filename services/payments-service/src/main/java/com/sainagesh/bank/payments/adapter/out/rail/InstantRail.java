package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.application.port.PaymentMessages;
import com.sainagesh.bank.payments.application.port.RailGateway;
import com.sainagesh.bank.payments.config.PaymentsProperties;
import com.sainagesh.bank.payments.domain.BusinessCalendar;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.domain.ReasonCode;
import com.sainagesh.bank.payments.iso20022.Pacs002;
import com.sainagesh.bank.payments.iso20022.Pacs008;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * The instant rail. One payment goes to the network as a pacs.008 message, and the network answers
 * with a pacs.002 within seconds. Yes means the receiver has the money and it cannot be taken back.
 *
 * <p>Both messages are stored, so the exact words exchanged with the network can be shown later.
 */
@Component
class InstantRail implements RailGateway {

    private final InstantNetworkSimulator network;
    private final PaymentMessages messages;
    private final PaymentsProperties properties;
    private final Clock clock;

    InstantRail(InstantNetworkSimulator network, PaymentMessages messages, PaymentsProperties properties, Clock clock) {
        this.network = network;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Rail rail() {
        return Rail.INSTANT;
    }

    @Override
    public Submission submit(Payment payment) {
        if (payment.amount().amount().compareTo(properties.instant().limit()) > 0) {
            return new Submission.Refused(
                    ReasonCode.AM02, "The instant rail carries at most " + properties.instant().limit().toPlainString() + " in one payment");
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(BusinessCalendar.EASTERN).toLocalDate();
        // ISO 20022 allows 35 characters for an id. A UUID with its hyphens is 36, without them 32.
        String id = payment.id().toString().replace("-", "");
        Pacs008 request = new Pacs008(
                "M" + id,
                now,
                id,
                payment.endToEndId(),
                id,
                payment.amount(),
                today,
                "Account holder",
                payment.debtorAccountId().toString(),
                properties.routingNumber(),
                payment.creditor().name(),
                payment.creditor().accountNumber(),
                payment.creditor().routingNumber(),
                payment.remittanceInformation());
        String requestXml = request.toXml();
        messages.record(payment.id(), "OUT", Pacs008.TYPE, requestXml);

        String answerXml = network.send(requestXml);
        messages.record(payment.id(), "IN", Pacs002.TYPE, answerXml);

        Pacs002 answer = Pacs002.parse(answerXml);
        if (answer.settled()) {
            return new Submission.Sent(answer.messageId(), today);
        }
        if (answer.rejected()) {
            return new Submission.Refused(ReasonCode.fromCode(answer.reasonCode()), answer.additionalInformation());
        }
        // Neither a yes nor a no, such as "pending". The receiver may still be paid, so this must
        // not be turned into a rejection. Failing here makes the saga ask again, and the network
        // gives one answer per transaction however often it is asked.
        throw new IllegalStateException("The instant network answered " + answer.status() + " for payment "
                + payment.id() + ", which is neither settled nor rejected");
    }

    /** The network's yes at submit time was final, so there is nothing more to wait for. */
    @Override
    public Outcome outcome(Payment payment) {
        return new Outcome.Settled();
    }
}
