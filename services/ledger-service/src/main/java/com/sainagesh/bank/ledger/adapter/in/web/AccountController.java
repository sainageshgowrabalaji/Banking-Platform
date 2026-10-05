package com.sainagesh.bank.ledger.adapter.in.web;

import com.sainagesh.bank.idempotency.RequestHash;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.AccountResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.BalanceResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.EntryPageResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.JournalEntryResponse;
import com.sainagesh.bank.ledger.adapter.in.web.Dtos.OpenAccountRequest;
import com.sainagesh.bank.ledger.application.AccountQueries;
import com.sainagesh.bank.ledger.application.ChangeAccountStatus;
import com.sainagesh.bank.ledger.application.OpenAccount;
import com.sainagesh.bank.ledger.application.port.Journal;
import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.web.ApiException;
import com.sainagesh.bank.web.IdempotencyKey;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Accounts, balances and account history. A controller only translates HTTP. The rules live in the domain. */
@RestController
@RequestMapping("/v1/accounts")
class AccountController {

    private final OpenAccount openAccount;
    private final ChangeAccountStatus changeStatus;
    private final AccountQueries queries;
    private final RequestHash requestHash;
    private final Clock clock;

    AccountController(
            OpenAccount openAccount,
            ChangeAccountStatus changeStatus,
            AccountQueries queries,
            RequestHash requestHash,
            Clock clock) {
        this.openAccount = openAccount;
        this.changeStatus = changeStatus;
        this.queries = queries;
        this.requestHash = requestHash;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody OpenAccountRequest request) {
        IdempotencyKey key = IdempotencyKey.fromHeader(idempotencyKey);
        // The bank's own accounts have no overdraft limit, so anyone who could open one could create
        // money. They are made by a database migration, reviewed like any other change, and never here.
        if (!request.type().customerOwned()) {
            throw ApiException.badRequest(
                    "VALIDATION_FAILED", "Only customer accounts can be opened here. The type must be CHECKING or SAVINGS");
        }
        if (request.customerId() == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "customerId is required");
        }
        Currency currency;
        try {
            currency = Currency.getInstance(request.currency());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("UNKNOWN_CURRENCY", request.currency() + " is not a currency");
        }
        Account account = openAccount.open(
                new OpenAccount.Command(request.customerId(), request.type(), request.name(), currency),
                key,
                requestHash.of(request));
        return ResponseEntity.created(URI.create("/v1/accounts/" + account.id())).body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    AccountResponse get(@PathVariable UUID accountId) {
        return AccountResponse.from(queries.account(accountId));
    }

    @GetMapping("/{accountId}/balance")
    BalanceResponse balance(@PathVariable UUID accountId) {
        return BalanceResponse.from(queries.account(accountId), clock.instant());
    }

    @GetMapping("/{accountId}/entries")
    EntryPageResponse entries(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        if (limit < 1 || limit > 200) {
            throw ApiException.badRequest("INVALID_PARAMETER", "limit must be from 1 to 200");
        }
        Journal.Page page = queries.entries(accountId, limit, cursor);
        return new EntryPageResponse(
                page.items().stream().map(JournalEntryResponse::from).toList(), page.nextCursor());
    }

    @PostMapping("/{accountId}/freeze")
    AccountResponse freeze(
            @PathVariable UUID accountId, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        IdempotencyKey.fromHeader(idempotencyKey);
        return AccountResponse.from(changeStatus.freeze(accountId));
    }

    @PostMapping("/{accountId}/unfreeze")
    AccountResponse unfreeze(
            @PathVariable UUID accountId, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        IdempotencyKey.fromHeader(idempotencyKey);
        return AccountResponse.from(changeStatus.unfreeze(accountId));
    }

    @PostMapping("/{accountId}/close")
    AccountResponse close(
            @PathVariable UUID accountId, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        IdempotencyKey.fromHeader(idempotencyKey);
        return AccountResponse.from(changeStatus.close(accountId));
    }
}
