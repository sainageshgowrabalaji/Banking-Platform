package com.sainagesh.bank.ledger.adapter.in.web;

import com.sainagesh.bank.ledger.domain.Account;
import com.sainagesh.bank.ledger.domain.AccountStatus;
import com.sainagesh.bank.ledger.domain.AccountType;
import com.sainagesh.bank.ledger.domain.EntrySide;
import com.sainagesh.bank.ledger.domain.Hold;
import com.sainagesh.bank.ledger.domain.HoldStatus;
import com.sainagesh.bank.ledger.domain.JournalEntry;
import com.sainagesh.bank.ledger.domain.Posting;
import com.sainagesh.bank.web.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The JSON shapes of the ledger API. They match {@code contracts/openapi/ledger-v1.yaml}.
 *
 * <p>These records are separate from the domain classes on purpose. The API can stay stable while the
 * domain changes, and the domain never has to know about JSON.
 */
final class Dtos {

    private Dtos() {}

    record OpenAccountRequest(
            UUID customerId,
            @NotNull AccountType type,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three letter currency code") String currency,
            @Size(max = 140) String name) {}

    record AccountResponse(
            UUID id,
            UUID customerId,
            AccountType type,
            String name,
            String currency,
            AccountStatus status,
            Instant openedAt,
            Instant closedAt) {

        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.id(),
                    account.customerId(),
                    account.type(),
                    account.name(),
                    account.currency().getCurrencyCode(),
                    account.status(),
                    account.openedAt(),
                    account.closedAt());
        }
    }

    record BalanceResponse(UUID accountId, MoneyDto ledger, MoneyDto available, MoneyDto held, Instant asOf) {

        static BalanceResponse from(Account account, Instant now) {
            return new BalanceResponse(
                    account.id(),
                    MoneyDto.from(account.ledgerBalance()),
                    MoneyDto.from(account.available()),
                    MoneyDto.from(account.held()),
                    now);
        }
    }

    record PostingDto(@NotNull UUID accountId, @NotNull EntrySide side, @NotNull @Valid MoneyDto amount) {

        static PostingDto from(Posting posting) {
            return new PostingDto(UUID.fromString(posting.accountId()), posting.side(), MoneyDto.from(posting.amount()));
        }

        Posting toDomain() {
            return new Posting(accountId.toString(), side, amount.toPositiveMoney());
        }
    }

    record PostJournalEntryRequest(
            @NotBlank @Size(max = 140) String reference,
            @NotNull @Size(min = 2, max = 200) List<@NotNull @Valid PostingDto> postings,
            UUID holdId) {}

    record JournalEntryResponse(UUID id, String reference, Instant postedAt, List<PostingDto> postings) {

        static JournalEntryResponse from(JournalEntry entry) {
            return new JournalEntryResponse(
                    entry.id(),
                    entry.reference(),
                    entry.postedAt(),
                    entry.postings().stream().map(PostingDto::from).toList());
        }
    }

    record EntryPageResponse(List<JournalEntryResponse> items, String nextCursor) {}

    record PlaceHoldRequest(
            @NotNull UUID accountId,
            @NotNull @Valid MoneyDto amount,
            @NotBlank @Size(max = 140) String reference,
            @Min(1) @Max(2_592_000) Long expiresInSeconds) {}

    record HoldResponse(
            UUID id,
            UUID accountId,
            MoneyDto amount,
            String reference,
            HoldStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant closedAt) {

        static HoldResponse from(Hold hold) {
            return new HoldResponse(
                    hold.id(),
                    hold.accountId(),
                    MoneyDto.from(hold.amount()),
                    hold.reference(),
                    hold.status(),
                    hold.createdAt(),
                    hold.expiresAt(),
                    hold.closedAt());
        }
    }
}
