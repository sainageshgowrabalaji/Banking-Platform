package com.sainagesh.bank.notification.application.port;

import java.util.UUID;

/** Remembers which events were handled, so an event that arrives twice is handled once. */
public interface Deduplication {

    /** Records the event id. True the first time, false when it was seen before. */
    boolean firstTime(UUID eventId);
}
