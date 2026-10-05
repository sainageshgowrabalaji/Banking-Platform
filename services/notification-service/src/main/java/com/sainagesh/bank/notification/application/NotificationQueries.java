package com.sainagesh.bank.notification.application;

import com.sainagesh.bank.notification.application.port.Notifications;
import com.sainagesh.bank.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read side. */
@Service
public class NotificationQueries {

    private final Notifications notifications;

    public NotificationQueries(Notifications notifications) {
        this.notifications = notifications;
    }

    public List<Notification> latestFor(UUID accountId, int limit) {
        return notifications.latestFor(accountId, limit);
    }
}
