package com.sainagesh.bank.payments.adapter.out.persistence;

import com.sainagesh.bank.payments.application.port.PaymentMessages;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Keeps the rail messages in the {@code payment_message} table. */
@Repository
class JdbcPaymentMessages implements PaymentMessages {

    private final JdbcClient jdbc;

    JdbcPaymentMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID paymentId, String direction, String type, String body) {
        // A step that is repeated after a failure sends the same message again. Keep one copy.
        jdbc.sql("""
                insert into payment_message (payment_id, direction, type, body)
                select :paymentId, :direction, :type, :body
                where not exists (
                    select 1 from payment_message
                    where payment_id = :paymentId and direction = :direction and type = :type)
                """)
                .param("paymentId", paymentId)
                .param("direction", direction)
                .param("type", type)
                .param("body", body)
                .update();
    }

    @Override
    public List<Message> of(UUID paymentId) {
        return jdbc.sql("select direction, type, body, created_at from payment_message where payment_id = :paymentId order by id")
                .param("paymentId", paymentId)
                .query((rs, n) -> new Message(
                        rs.getString("direction"),
                        rs.getString("type"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
