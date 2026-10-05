package com.sainagesh.bank.notification.application.port;

import com.sainagesh.bank.notification.domain.Notification;

/** Delivers a message to the customer. A real one would call an email, SMS or push provider. */
public interface Sender {

    void send(Notification notification);
}
