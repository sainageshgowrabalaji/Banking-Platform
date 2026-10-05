package com.sainagesh.bank.notification.adapter.out.persistence;

import com.sainagesh.bank.messaging.ProcessedEvents;
import com.sainagesh.bank.notification.application.port.Deduplication;
import com.sainagesh.bank.notification.application.port.Notifications;
import com.sainagesh.bank.notification.domain.Channel;
import com.sainagesh.bank.notification.domain.Notification;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Keeps messages and handled event ids in PostgreSQL. */
@Repository
class JdbcNotifications implements Notifications, Deduplication {

    private static final String CONSUMER = "notifications";

    private final JdbcClient jdbc;
    private final ProcessedEvents processedEvents;

    JdbcNotifications(JdbcClient jdbc, ProcessedEvents processedEvents) {
        this.jdbc = jdbc;
        this.processedEvents = processedEvents;
    }

    @Override
    public boolean firstTime(UUID eventId) {
        return processedEvents.firstTime(CONSUMER, eventId);
    }

    @Override
    public void save(Notification notification) {
        jdbc.sql("""
                insert into notification (id, event_id, event_type, account_id, channel, title, body, created_at)
                values (:id, :eventId, :eventType, :accountId, :channel, :title, :body, :createdAt)
                """)
                .param("id", notification.id())
                .param("eventId", notification.eventId())
                .param("eventType", notification.eventType())
                .param("accountId", notification.accountId())
                .param("channel", notification.channel().name())
                .param("title", notification.title())
                .param("body", notification.body())
                .param("createdAt", Timestamp.from(notification.createdAt()))
                .update();
    }

    @Override
    public List<Notification> latestFor(UUID accountId, int limit) {
        return jdbc.sql("""
                select id, event_id, event_type, account_id, channel, title, body, created_at
                from notification
                where account_id = :accountId
                order by seq desc
                limit :limit
                """)
                .param("accountId", accountId)
                .param("limit", limit)
                .query((rs, n) -> new Notification(
                        rs.getObject("id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getObject("account_id", UUID.class),
                        Channel.valueOf(rs.getString("channel")),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
