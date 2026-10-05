package com.sainagesh.bank.ledger.config;

import com.sainagesh.bank.idempotency.IdempotencyStore;
import com.sainagesh.bank.ledger.application.ReleaseHold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Small jobs that keep the ledger tidy. */
@Component
class Housekeeping {

    private static final Logger log = LoggerFactory.getLogger(Housekeeping.class);

    private final ReleaseHold releaseHold;
    private final IdempotencyStore idempotency;
    private final LedgerProperties properties;

    Housekeeping(ReleaseHold releaseHold, IdempotencyStore idempotency, LedgerProperties properties) {
        this.releaseHold = releaseHold;
        this.idempotency = idempotency;
        this.properties = properties;
    }

    /** A hold nobody captured or released gives its money back when its time runs out. */
    @Scheduled(fixedDelayString = "${bank.ledger.hold-expiry-check:PT1M}", initialDelayString = "PT30S")
    void releaseExpiredHolds() {
        int released = releaseHold.releaseExpired(200);
        if (released > 0) {
            log.info("Released {} expired holds", released);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void forgetOldIdempotencyKeys() {
        int removed = idempotency.purgeOlderThan(properties.idempotencyRetention());
        if (removed > 0) {
            log.info("Forgot {} old idempotency keys", removed);
        }
    }
}
