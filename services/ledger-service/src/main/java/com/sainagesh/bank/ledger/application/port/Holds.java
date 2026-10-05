package com.sainagesh.bank.ledger.application.port;

import com.sainagesh.bank.ledger.domain.Hold;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where holds are kept. */
public interface Holds {

    Optional<Hold> find(UUID id);

    Optional<Hold> findByReference(String reference);

    /** The account a hold is on, read without loading the hold itself. */
    Optional<UUID> accountOf(UUID holdId);

    void save(Hold hold);

    /** Ids of active holds whose time has run out. */
    List<UUID> findExpired(Instant now, int limit);
}
