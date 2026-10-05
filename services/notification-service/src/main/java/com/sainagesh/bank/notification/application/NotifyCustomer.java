package com.sainagesh.bank.notification.application;

import com.sainagesh.bank.notification.application.port.Deduplication;
import com.sainagesh.bank.notification.application.port.Notifications;
import com.sainagesh.bank.notification.application.port.Sender;
import com.sainagesh.bank.notification.domain.EventFacts;
import com.sainagesh.bank.notification.domain.Notification;
import com.sainagesh.bank.notification.domain.Templates;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one event into one message for the customer, exactly once.
 *
 * <p>Kafka promises that an event arrives at least once, which means it can arrive twice. The first
 * thing done here, inside the transaction, is to record the event id. If the id is already there, the
 * event was handled before and nothing more happens. The id and the message are saved together, so
 * there is never an id without its message or a message sent twice.
 */
@Service
public class NotifyCustomer {

    private static final Logger log = LoggerFactory.getLogger(NotifyCustomer.class);

    private final Deduplication deduplication;
    private final Notifications notifications;
    private final Sender sender;
    private final Clock clock;

    public NotifyCustomer(Deduplication deduplication, Notifications notifications, Sender sender, Clock clock) {
        this.deduplication = deduplication;
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
    }

    /** What happened to the event. */
    public enum Result {
        SENT,
        DUPLICATE,
        NOT_FOR_CUSTOMERS
    }

    @Transactional
    public Result handle(EventFacts facts) {
        if (!deduplication.firstTime(facts.eventId())) {
            log.debug("Event {} was handled before. Skipped.", facts.eventId());
            return Result.DUPLICATE;
        }
        Optional<Notification> message = Templates.messageFor(facts, clock.instant());
        if (message.isEmpty()) {
            return Result.NOT_FOR_CUSTOMERS;
        }
        notifications.save(message.get());
        sender.send(message.get());
        return Result.SENT;
    }
}
