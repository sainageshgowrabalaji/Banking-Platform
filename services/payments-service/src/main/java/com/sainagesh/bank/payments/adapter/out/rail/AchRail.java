package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.application.port.RailGateway;
import com.sainagesh.bank.payments.config.PaymentsProperties;
import com.sainagesh.bank.payments.domain.BusinessCalendar;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The ACH rail. A payment does not leave at once. It waits as an entry until the next batch is cut,
 * travels with that batch, and settles on a later business day. Until then its outcome is "pending".
 *
 * <p>The batch itself is built and settled by {@link AchBatchJobs}.
 */
@Component
class AchRail implements RailGateway {

    private final JdbcClient jdbc;
    private final BusinessCalendar calendar;
    private final PaymentsProperties properties;
    private final Clock clock;

    AchRail(JdbcClient jdbc, BusinessCalendar calendar, PaymentsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Rail rail() {
        return Rail.ACH;
    }

    /** Queues the payment for the next batch. Asking twice queues it once. */
    @Override
    public Submission submit(Payment payment) {
        jdbc.sql("""
                insert into ach_entry (payment_id, trace_number, status)
                values (:paymentId, :routing || lpad(nextval('ach_trace_seq')::text, 7, '0'), 'QUEUED')
                on conflict (payment_id) do nothing
                """)
                .param("paymentId", payment.id())
                .param("routing", properties.routingNumber().substring(0, 8))
                .update();
        String trace = jdbc.sql("select trace_number from ach_entry where payment_id = :paymentId")
                .param("paymentId", payment.id())
                .query(String.class)
                .single();
        return new Submission.Sent(trace, calendar.batchSettlementDate(clock.instant()));
    }

    @Override
    public Outcome outcome(Payment payment) {
        record Entry(String status, String returnCode) {}
        Entry entry = jdbc.sql("select status, return_code from ach_entry where payment_id = :paymentId")
                .param("paymentId", payment.id())
                .query((rs, n) -> new Entry(rs.getString("status"), rs.getString("return_code")))
                .single();
        return switch (entry.status()) {
            case "SETTLED" -> new Outcome.Settled();
            case "RETURNED" -> {
                ReasonCode reason = fromReturnCode(entry.returnCode());
                yield new Outcome.Returned(reason, "ACH return " + entry.returnCode() + ". " + reason.meaning());
            }
            default -> new Outcome.Pending(properties.ach().pollInterval());
        };
    }

    /** ACH has its own return codes (R01, R02 ...). They are mapped to the ISO 20022 reason a payment carries. */
    static ReasonCode fromReturnCode(String returnCode) {
        return switch (returnCode == null ? "" : returnCode) {
            case "R02" -> ReasonCode.AC04;
            case "R03", "R04" -> ReasonCode.AC01;
            case "R16" -> ReasonCode.AC06;
            default -> ReasonCode.MS03;
        };
    }
}
