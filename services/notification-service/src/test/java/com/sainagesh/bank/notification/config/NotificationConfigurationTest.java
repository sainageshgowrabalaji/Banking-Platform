package com.sainagesh.bank.notification.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;

class NotificationConfigurationTest {

    @Test
    void aDatabaseThatCannotBeReachedIsAnOutageHoweverDeepItIsWrapped() {
        Exception noConnection = new CannotGetJdbcConnectionException("no connection");
        Exception noTransaction =
                new CannotCreateTransactionException("no transaction", new SQLTransientConnectionException("pool is empty"));

        assertTrue(NotificationConfiguration.databaseIsDown(noConnection));
        assertTrue(NotificationConfiguration.databaseIsDown(new ListenerExecutionFailedException("failed", noTransaction)));
    }

    @Test
    void aFaultInTheEventItselfIsNotAnOutage() {
        assertFalse(NotificationConfiguration.databaseIsDown(new IllegalStateException("bad event")));
        assertFalse(NotificationConfiguration.databaseIsDown(new DataIntegrityViolationException("too long")));
    }
}
