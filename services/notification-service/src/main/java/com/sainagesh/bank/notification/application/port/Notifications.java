package com.sainagesh.bank.notification.application.port;

import com.sainagesh.bank.notification.domain.Notification;
import java.util.List;
import java.util.UUID;

/** Where sent messages are kept, so the app can show a customer their history. */
public interface Notifications {

    void save(Notification notification);

    /** The newest messages about an account, newest first. */
    List<Notification> latestFor(UUID accountId, int limit);
}
