package com.sainagesh.bank.messaging;

import java.util.function.Supplier;

/** Gives the W3C trace context of the work in progress on this thread, or null when there is none. */
@FunctionalInterface
public interface TraceparentSource extends Supplier<String> {}
