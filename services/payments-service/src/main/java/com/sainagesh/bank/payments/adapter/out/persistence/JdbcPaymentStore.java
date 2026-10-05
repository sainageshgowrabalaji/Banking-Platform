package com.sainagesh.bank.payments.adapter.out.persistence;

import com.sainagesh.bank.money.Money;
import com.sainagesh.bank.payments.application.InvalidCursorException;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.Party;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Keeps payments in PostgreSQL with plain SQL.
 *
 * <p>The ledger uses JPA. This service uses SQL through Spring's JdbcClient, to show the other common
 * way. With SQL every statement is in plain sight, which suits the saga's claim and schedule queries.
 */
@Repository
class JdbcPaymentStore implements PaymentStore {

    private static final String COLUMNS = """
            id, seq, debtor_account_id, creditor_name, creditor_account, creditor_routing, amount_minor, currency,
            rail, status, reason_code, reason_detail, end_to_end_id, remittance_info, hold_id, hold_release_pending,
            rail_reference, settlement_date, created_at, settled_at, version
            """;

    private final JdbcClient jdbc;

    JdbcPaymentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Payment payment, Instant firstAttemptAt) {
        jdbc.sql("""
                insert into payment (id, debtor_account_id, creditor_name, creditor_account, creditor_routing,
                                     amount_minor, currency, rail, status, end_to_end_id, remittance_info,
                                     created_at, next_attempt_at)
                values (:id, :debtor, :creditorName, :creditorAccount, :creditorRouting,
                        :amount, :currency, :rail, :status, :endToEndId, :remittance,
                        :createdAt, :nextAttemptAt)
                """)
                .param("id", payment.id())
                .param("debtor", payment.debtorAccountId())
                .param("creditorName", payment.creditor().name())
                .param("creditorAccount", payment.creditor().accountNumber())
                .param("creditorRouting", payment.creditor().routingNumber())
                .param("amount", payment.amount().toMinorUnits())
                .param("currency", payment.amount().currency().getCurrencyCode())
                .param("rail", payment.rail().name())
                .param("status", payment.status().name())
                .param("endToEndId", payment.endToEndId())
                .param("remittance", payment.remittanceInformation())
                .param("createdAt", Timestamp.from(payment.createdAt()))
                .param("nextAttemptAt", Timestamp.from(firstAttemptAt))
                .update();
    }

    @Override
    public Optional<Payment> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from payment where id = :id")
                .param("id", id)
                .query(JdbcPaymentStore::toPayment)
                .optional();
    }

    /**
     * The "where version = :version" is optimistic locking by hand. If someone else changed the row
     * since it was read, no row matches, and the change is refused instead of overwriting theirs.
     */
    @Override
    public void update(Payment payment, Instant nextAttemptAt) {
        int changed = jdbc.sql("""
                update payment
                set status = :status, reason_code = :reasonCode, reason_detail = :reasonDetail, hold_id = :holdId,
                    hold_release_pending = :holdReleasePending, rail_reference = :railReference,
                    settlement_date = :settlementDate, settled_at = :settledAt, updated_at = now(),
                    version = version + 1, next_attempt_at = :nextAttemptAt, locked_until = null,
                    attempts = 0, failures = 0, parked_at = null, last_error = null
                where id = :id and version = :version
                """)
                .param("status", payment.status().name())
                .param("reasonCode", payment.reasonCode() == null ? null : payment.reasonCode().name())
                .param("reasonDetail", payment.reasonDetail())
                .param("holdId", payment.holdId())
                .param("holdReleasePending", payment.holdReleasePending())
                .param("railReference", payment.railReference())
                .param("settlementDate", payment.settlementDate() == null ? null : Date.valueOf(payment.settlementDate()))
                .param("settledAt", payment.settledAt() == null ? null : Timestamp.from(payment.settledAt()))
                .param("nextAttemptAt", nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt))
                .param("id", payment.id())
                .param("version", payment.version())
                .update();
        if (changed != 1) {
            throw new OptimisticLockingFailureException("Payment " + payment.id() + " was changed by someone else");
        }
    }

    /** One statement both checks that the payment is free and takes it, so two workers cannot both win. */
    @Override
    public Optional<Payment> claim(UUID id, Duration claimFor) {
        return jdbc.sql("update payment set locked_until = now() + make_interval(secs => :seconds) "
                        + "where id = :id and (locked_until is null or locked_until < now()) returning " + COLUMNS)
                .param("seconds", claimFor.toSeconds())
                .param("id", id)
                .query(JdbcPaymentStore::toPayment)
                .optional();
    }

    @Override
    public void releaseClaim(UUID id) {
        jdbc.sql("update payment set locked_until = null where id = :id").param("id", id).update();
    }

    @Override
    public void done(UUID id) {
        jdbc.sql("update payment set locked_until = null, next_attempt_at = null where id = :id")
                .param("id", id)
                .update();
    }

    @Override
    public boolean retryOrPark(UUID id, Instant now, String error, int parkAfter) {
        return jdbc.sql("""
                update payment
                set attempts = attempts + 1,
                    failures = failures + 1,
                    next_attempt_at = case when failures + 1 >= :parkAfter then null
                        else cast(:now as timestamptz) + make_interval(secs => least(60, power(2, least(attempts, 10) + 1))) end,
                    parked_at = case when failures + 1 >= :parkAfter then cast(:now as timestamptz) else null end,
                    locked_until = null,
                    last_error = :error
                where id = :id
                returning parked_at is not null
                """)
                .param("parkAfter", parkAfter)
                .param("now", Timestamp.from(now))
                .param("error", error)
                .param("id", id)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    @Override
    public long countParked() {
        return jdbc.sql("select count(*) from payment where parked_at is not null").query(Long.class).single();
    }

    @Override
    public void retryLater(UUID id, Instant now, String error) {
        jdbc.sql("""
                update payment
                set attempts = attempts + 1,
                    next_attempt_at = cast(:now as timestamptz) + make_interval(secs => least(60, power(2, least(attempts, 10) + 1))),
                    locked_until = null,
                    last_error = :error
                where id = :id
                """)
                .param("now", Timestamp.from(now))
                .param("error", error)
                .param("id", id)
                .update();
    }

    @Override
    public void lookAgainAt(UUID id, Instant when) {
        jdbc.sql("update payment set next_attempt_at = :when, locked_until = null where id = :id")
                .param("when", Timestamp.from(when))
                .param("id", id)
                .update();
    }

    @Override
    public List<UUID> findDue(Instant now, int limit) {
        return jdbc.sql("""
                select id from payment
                where next_attempt_at <= :now and (locked_until is null or locked_until < now())
                order by next_attempt_at
                limit :limit
                """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public void wake(UUID id) {
        jdbc.sql("update payment set next_attempt_at = now() where id = :id and next_attempt_at is not null")
                .param("id", id)
                .update();
    }

    @Override
    public Page listByAccount(UUID debtorAccountId, PaymentStatus status, int limit, String cursor) {
        long before = cursor == null ? Long.MAX_VALUE : decode(cursor);
        record Row(Payment payment, long seq) {}
        List<Row> found = jdbc.sql("select " + COLUMNS + """
                         from payment
                        where debtor_account_id = :account and seq < :before
                          and (cast(:status as text) is null or status = :status)
                        order by seq desc
                        limit :limit
                        """)
                .param("account", debtorAccountId)
                .param("before", before)
                .param("status", status == null ? null : status.name())
                .param("limit", limit + 1)
                .query((rs, n) -> new Row(toPayment(rs, n), rs.getLong("seq")))
                .list();
        boolean more = found.size() > limit;
        List<Row> page = more ? found.subList(0, limit) : found;
        String next = more ? encode(page.get(page.size() - 1).seq()) : null;
        return new Page(page.stream().map(Row::payment).toList(), next);
    }

    private static Payment toPayment(ResultSet rs, int rowNumber) throws SQLException {
        String currency = rs.getString("currency");
        String reason = rs.getString("reason_code");
        Date settlementDate = rs.getDate("settlement_date");
        Timestamp settledAt = rs.getTimestamp("settled_at");
        return Payment.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("debtor_account_id", UUID.class),
                new Party(rs.getString("creditor_name"), rs.getString("creditor_account"), rs.getString("creditor_routing")),
                Money.ofMinorUnits(rs.getLong("amount_minor"), currency),
                Rail.valueOf(rs.getString("rail")),
                rs.getString("end_to_end_id"),
                rs.getString("remittance_info"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"),
                new Payment.State(
                        PaymentStatus.valueOf(rs.getString("status")),
                        reason == null ? null : ReasonCode.valueOf(reason),
                        rs.getString("reason_detail"),
                        rs.getObject("hold_id", UUID.class),
                        rs.getBoolean("hold_release_pending"),
                        rs.getString("rail_reference"),
                        settlementDate == null ? null : settlementDate.toLocalDate(),
                        settledAt == null ? null : settledAt.toInstant()));
    }

    private static String encode(long seq) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(seq).getBytes(StandardCharsets.UTF_8));
    }

    private static long decode(String cursor) {
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }
}
