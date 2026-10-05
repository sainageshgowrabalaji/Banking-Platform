package com.sainagesh.bank.notification.adapter.out.messaging;

import com.sainagesh.bank.notification.application.port.Sender;
import com.sainagesh.bank.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stands in for an email, SMS or push provider. It writes the message to the log, which is enough to
 * watch the flow work on a laptop. A real provider is one more class behind the same port.
 */
@Component
class LoggingSender implements Sender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSender.class);

    @Override
    public void send(Notification notification) {
        log.info("To account {} via {}: {}. {}", notification.accountId(), notification.channel(), notification.title(), notification.body());
    }
}
