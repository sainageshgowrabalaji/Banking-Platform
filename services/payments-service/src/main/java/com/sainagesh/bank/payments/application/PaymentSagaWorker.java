package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.payments.application.port.PaymentStore;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The background worker of the saga. It looks for payments whose next step is due and moves each one
 * forward.
 *
 * <p>Most payments never need it, because their steps run in the request that created them. The
 * worker is what finishes the rest. A payment that was waiting for a batch to settle, one that hit a
 * ledger that was down, or one that was in flight when the service was restarted.
 */
@Component
public class PaymentSagaWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaWorker.class);

    private final PaymentStore payments;
    private final PaymentSaga saga;
    private final Clock clock;
    private final Duration pollInterval;
    private final boolean enabled;

    private volatile boolean running;
    private Thread thread;

    public PaymentSagaWorker(
            PaymentStore payments,
            PaymentSaga saga,
            Clock clock,
            @Value("${bank.payments.worker.poll-interval:PT1S}") Duration pollInterval,
            @Value("${bank.payments.worker.enabled:true}") boolean enabled) {
        this.payments = payments;
        this.saga = saga;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.enabled = enabled;
    }

    /** One pass. Returns how many payments were looked at. Public so a test can drive it by hand. */
    public int runOnce() {
        List<UUID> due = payments.findDue(clock.instant(), 50);
        for (UUID id : due) {
            try {
                saga.advance(id);
            } catch (RuntimeException e) {
                log.warn("Payment {} could not be advanced. It will be tried again. {}", id, e.toString());
            }
        }
        return due.size();
    }

    private void loop() {
        while (running) {
            int handled = 0;
            try {
                handled = runOnce();
            } catch (RuntimeException e) {
                log.warn("Saga worker pass failed. It will run again. {}", e.toString());
            }
            if (handled < 50) {
                try {
                    Thread.sleep(pollInterval.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("payment-saga-worker").start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 200;
    }
}
