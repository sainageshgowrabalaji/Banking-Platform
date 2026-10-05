package com.sainagesh.bank.ledger.adapter.in.web;

import com.sainagesh.bank.ledger.adapter.in.web.Dtos.JournalEntryResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.PostJournalEntryRequest;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.PostingDto;
import com.sainagesh.bank.ledger.application.PostJournalEntry;
import com.sainagesh.bank.web.IdempotencyKey;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Posting journal entries. Internal. Other services call this, never a customer.
 *
 * <p>The {@code reference} in the body is what makes a retry safe here. One reference is posted once,
 * and posting it again returns the first entry. The {@code Idempotency-Key} header is still required,
 * so every write in the platform looks the same to a client.
 */
@RestController
@RequestMapping("/v1/journal-entries")
class JournalController {

    private final PostJournalEntry postJournalEntry;

    JournalController(PostJournalEntry postJournalEntry) {
        this.postJournalEntry = postJournalEntry;
    }

    @PostMapping
    ResponseEntity<JournalEntryResponse> post(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey,
            @Valid @RequestBody PostJournalEntryRequest request) {
        IdempotencyKey.fromHeader(idempotencyKey);
        PostJournalEntry.Result result = postJournalEntry.post(new PostJournalEntry.Command(
                request.reference(), request.postings().stream().map(PostingDto::toDomain).toList(), request.holdId()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(JournalEntryResponse.from(result.entry()));
    }
}
