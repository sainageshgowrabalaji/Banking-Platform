package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.domain.ReasonCode;
import com.sainagesh.bank.payments.iso20022.Pacs002;
import com.sainagesh.bank.payments.iso20022.Pacs008;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A stand-in for an instant payment network such as FedNow or RTP.
 *
 * <p>It speaks the real protocol. A pacs.008 message goes in and a pacs.002 status report comes out,
 * both as ISO 20022 XML, so the code that talks to it is the code that would talk to a real network.
 *
 * <p>Like a real network it remembers the transaction ids it has seen. A payment that is sent twice,
 * because the sender never got the first answer, gets that first answer again and is not paid twice.
 */
@Component
class InstantNetworkSimulator {

    private final Clock clock;
    private final Map<String, String> answers = new ConcurrentHashMap<>();

    InstantNetworkSimulator(Clock clock) {
        this.clock = clock;
    }

    /** @param pacs008Xml the payment, as a pacs.008 message */
    String send(String pacs008Xml) {
        Pacs008 payment = Pacs008.parse(pacs008Xml);
        return answers.computeIfAbsent(payment.transactionId(), id -> decide(payment).toXml());
    }

    private Pacs002 decide(Pacs008 payment) {
        Optional<ReasonCode> problem = SimulatedReceivers.problemWith(payment.creditorAccount());
        return new Pacs002(
                "NET-" + UUID.randomUUID(),
                clock.instant(),
                payment.messageId(),
                payment.endToEndId(),
                payment.transactionId(),
                problem.isPresent() ? "RJCT" : "ACSC",
                problem.map(ReasonCode::name).orElse(null),
                problem.map(ReasonCode::meaning).orElse(null));
    }
}
