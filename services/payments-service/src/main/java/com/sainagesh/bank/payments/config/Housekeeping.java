package com.sainagesh.bank.payments.config;

import com.sainagesh.bank.idempotency.IdempotencyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Small jobs that keep the payments database tidy. */
@Component
class Housekeeping {

    private static final Logger log = LoggerFactory.getLogger(Housekeeping.class);

    private final IdempotencyStore idempotency;
    private final PaymentsProperties properties;

    Housekeeping(IdempotencyStore idempotency, PaymentsProperties properties) {
        this.idempotency = idempotency;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void forgetOldIdempotencyKeys() {
        int removed = idempotency.purgeOlderThan(properties.idempotencyRetention());
        if (removed > 0) {
            log.info("Forgot {} old idempotency keys", removed);
        }
    }
}
