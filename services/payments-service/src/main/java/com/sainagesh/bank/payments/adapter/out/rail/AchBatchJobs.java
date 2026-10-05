package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.application.port.PaymentMessages;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.config.PaymentsProperties;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two halves of the simulated ACH network.
 *
 * <p>{@link #cutBatch()} is the bank's side. At each cut-off it gathers the waiting entries into one
 * batch file and "sends" it. {@link #settleBatches()} is the network's side. Some time later it settles
 * the batch, returns the entries the receiving bank refused, and wakes the saga of each payment.
 *
 * <p>The real network cuts a few batches a day and settles on the next business day. On a laptop both
 * waits are a few seconds, set in the settings, so a demo does not take until tomorrow.
 */
@Component
class AchBatchJobs {

    private static final Logger log = LoggerFactory.getLogger(AchBatchJobs.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final PaymentStore payments;
    private final PaymentMessages messages;
    private final PaymentsProperties properties;
    private final Clock clock;

    AchBatchJobs(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            PaymentStore payments,
            PaymentMessages messages,
            PaymentsProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.payments = payments;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    private record Entry(UUID paymentId, String trace, String name, String account, String routing, long amountMinor) {}

    /** Gathers every waiting entry into one batch. Returns how many entries went into it. */
    @Scheduled(fixedDelayString = "${bank.payments.ach.batch-interval:PT15S}", initialDelayString = "PT5S")
    public int cutBatch() {
        Integer count = transaction.execute(status -> {
            // "skip locked" lets two copies of the service cut batches at the same time without
            // ever putting one entry into two batches.
            List<Entry> entries = jdbc.sql("""
                    select e.payment_id, e.trace_number, p.creditor_name, p.creditor_account, p.creditor_routing, p.amount_minor
                    from ach_entry e
                    join payment p on p.id = e.payment_id
                    where e.status = 'QUEUED' and p.status = 'ACSP'
                    order by e.created_at
                    limit 500
                    for update of e skip locked
                    """)
                    .query((rs, n) -> new Entry(
                            rs.getObject("payment_id", UUID.class),
                            rs.getString("trace_number"),
                            rs.getString("creditor_name"),
                            rs.getString("creditor_account"),
                            rs.getString("creditor_routing"),
                            rs.getLong("amount_minor")))
                    .list();
            if (entries.isEmpty()) {
                return 0;
            }
            UUID batchId = UUID.randomUUID();
            Instant now = clock.instant();
            long total = entries.stream().mapToLong(Entry::amountMinor).sum();
            jdbc.sql("""
                    insert into ach_batch (id, status, entry_count, total_minor, file, submitted_at)
                    values (:id, 'SUBMITTED', :count, :total, :file, :now)
                    """)
                    .param("id", batchId)
                    .param("count", entries.size())
                    .param("total", total)
                    .param("file", file(batchId, now, entries, total))
                    .param("now", Timestamp.from(now))
                    .update();
            jdbc.sql("update ach_entry set status = 'SUBMITTED', batch_id = :batch, updated_at = now() where payment_id in (:ids)")
                    .param("batch", batchId)
                    .param("ids", entries.stream().map(Entry::paymentId).toList())
                    .update();
            return entries.size();
        });
        if (count != null && count > 0) {
            log.info("Cut an ACH batch with {} entries", count);
        }
        return count == null ? 0 : count;
    }

    /** Settles every batch that has waited long enough. Returns how many batches were settled. */
    @Scheduled(fixedDelayString = "${bank.payments.ach.settle-check:PT2S}", initialDelayString = "PT5S")
    public int settleBatches() {
        List<UUID> due = jdbc.sql("select id from ach_batch where status = 'SUBMITTED' and submitted_at <= :before order by submitted_at")
                .param("before", Timestamp.from(clock.instant().minus(properties.ach().settleAfter())))
                .query(UUID.class)
                .list();
        for (UUID batchId : due) {
            settle(batchId);
        }
        return due.size();
    }

    private void settle(UUID batchId) {
        record Line(UUID paymentId, String account) {}
        List<UUID> affected = transaction.execute(status -> {
            int claimed = jdbc.sql("update ach_batch set status = 'SETTLED', settled_at = now() where id = :id and status = 'SUBMITTED'")
                    .param("id", batchId)
                    .update();
            if (claimed == 0) {
                return List.<UUID>of();
            }
            List<Line> lines = jdbc.sql("""
                    select e.payment_id, p.creditor_account
                    from ach_entry e join payment p on p.id = e.payment_id
                    where e.batch_id = :batch
                    """)
                    .param("batch", batchId)
                    .query((rs, n) -> new Line(rs.getObject("payment_id", UUID.class), rs.getString("creditor_account")))
                    .list();
            for (Line line : lines) {
                Optional<String> returnCode = SimulatedReceivers.problemWith(line.account()).map(AchBatchJobs::returnCodeFor);
                jdbc.sql("update ach_entry set status = :status, return_code = :code, updated_at = now() where payment_id = :id")
                        .param("status", returnCode.isPresent() ? "RETURNED" : "SETTLED")
                        .param("code", returnCode.orElse(null))
                        .param("id", line.paymentId())
                        .update();
                if (returnCode.isPresent()) {
                    messages.record(line.paymentId(), "IN", "ACH return", "Return " + returnCode.get() + " for batch " + batchId);
                }
            }
            return lines.stream().map(Line::paymentId).toList();
        });
        // Tell the saga there is news, so it does not have to wait for its next look.
        if (affected != null) {
            affected.forEach(payments::wake);
            log.info("Settled ACH batch {} with {} entries", batchId, affected.size());
        }
    }

    private static String returnCodeFor(ReasonCode reason) {
        return switch (reason) {
            case AC04 -> "R02";
            case AC01 -> "R03";
            case AC06 -> "R16";
            default -> "R17";
        };
    }

    /**
     * A simplified picture of an ACH file. A real NACHA file has fixed-width records of 94 characters.
     * The record types are the same (1 file header, 5 batch header, 6 entry, 8 batch control, 9 file
     * control), written here so a person can read them.
     */
    private String file(UUID batchId, Instant now, List<Entry> entries, long totalMinor) {
        String total = BigDecimal.valueOf(totalMinor, 2).toPlainString();
        StringBuilder file = new StringBuilder();
        file.append("1 FILE HEADER    origin=").append(properties.routingNumber()).append(" created=").append(now).append('\n');
        file.append("5 BATCH HEADER   batch=").append(batchId).append(" class=PPD entries=").append(entries.size()).append('\n');
        for (Entry entry : entries) {
            String account = entry.account();
            String masked = account.length() <= 4 ? account : "****" + account.substring(account.length() - 4);
            file.append("6 ENTRY          trace=").append(entry.trace())
                    .append(" routing=").append(entry.routing())
                    .append(" account=").append(masked)
                    .append(" amount=").append(BigDecimal.valueOf(entry.amountMinor(), 2).toPlainString())
                    .append(" name=").append(entry.name())
                    .append('\n');
        }
        file.append("8 BATCH CONTROL  entries=").append(entries.size()).append(" total=").append(total).append('\n');
        file.append("9 FILE CONTROL   batches=1 entries=").append(entries.size()).append(" total=").append(total).append('\n');
        return file.toString();
    }
}
