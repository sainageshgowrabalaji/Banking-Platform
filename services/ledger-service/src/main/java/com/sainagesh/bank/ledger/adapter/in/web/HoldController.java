package com.sainagesh.bank.ledger.adapter.in.web;

import com.sainagesh.bank.ledger.adapter.in.web.Dtos.HoldResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.PlaceHoldRequest;
import com.sainagesh.bank.ledger.application.AccountQueries;
import com.sainagesh.bank.ledger.application.PlaceHold;
import com.sainagesh.bank.ledger.application.ReleaseHold;
import com.sainagesh.bank.ledger.config.LedgerProperties;
import com.sainagesh.bank.web.IdempotencyKey;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Holds. Internal. The payments service reserves money here before a payment settles. */
@RestController
@RequestMapping("/v1/holds")
class HoldController {

    private final PlaceHold placeHold;
    private final ReleaseHold releaseHold;
    private final AccountQueries queries;
    private final LedgerProperties properties;

    HoldController(PlaceHold placeHold, ReleaseHold releaseHold, AccountQueries queries, LedgerProperties properties) {
        this.placeHold = placeHold;
        this.releaseHold = releaseHold;
        this.queries = queries;
        this.properties = properties;
    }

    @PostMapping
    ResponseEntity<HoldResponse> place(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody PlaceHoldRequest request) {
        IdempotencyKey.fromHeader(idempotencyKey);
        Duration lifetime = request.expiresInSeconds() == null
                ? properties.holdLifetime()
                : Duration.ofSeconds(request.expiresInSeconds());
        PlaceHold.Result result = placeHold.place(
                new PlaceHold.Command(request.accountId(), request.amount().toPositiveMoney(), request.reference(), lifetime));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(HoldResponse.from(result.hold()));
    }

    @GetMapping("/{holdId}")
    HoldResponse get(@PathVariable UUID holdId) {
        return HoldResponse.from(queries.hold(holdId));
    }

    @PostMapping("/{holdId}/release")
    HoldResponse release(@PathVariable UUID holdId, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        IdempotencyKey.fromHeader(idempotencyKey);
        return HoldResponse.from(releaseHold.release(holdId));
    }
}
