package com.sainagesh.bank.payments.adapter.in.web;

import com.sainagesh.bank.payments.application.port.PaymentMessages;
import com.sainagesh.bank.payments.domain.Party;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.web.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The JSON shapes of the payments API. They match {@code contracts/openapi/payments-v1.yaml}. */
final class Dtos {

    private Dtos() {}

    record PartyDto(
            @NotBlank @Size(max = 140) String name,
            @NotBlank @Size(max = 64) String accountNumber,
            @Pattern(regexp = "^[0-9]{9}$", message = "must be nine digits") String routingNumber) {

        static PartyDto from(Party party) {
            return new PartyDto(party.name(), party.accountNumber(), party.routingNumber());
        }

        Party toDomain() {
            return new Party(name, accountNumber, routingNumber);
        }
    }

    record CreatePaymentRequest(
            @NotNull UUID debtorAccountId,
            @NotNull @Valid PartyDto creditor,
            @NotNull @Valid MoneyDto amount,
            @NotNull Rail rail,
            @Size(max = 35) String endToEndId,
            @Size(max = 140) String remittanceInformation) {}

    record PaymentResponse(
            UUID id,
            UUID debtorAccountId,
            PartyDto creditor,
            MoneyDto amount,
            Rail rail,
            PaymentStatus status,
            String reasonCode,
            String reasonDetail,
            String endToEndId,
            String remittanceInformation,
            LocalDate settlementDate,
            Instant createdAt,
            Instant settledAt) {

        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.id(),
                    payment.debtorAccountId(),
                    PartyDto.from(payment.creditor()),
                    MoneyDto.from(payment.amount()),
                    payment.rail(),
                    payment.status(),
                    payment.reasonCode() == null ? null : payment.reasonCode().name(),
                    payment.reasonDetail(),
                    payment.endToEndId(),
                    payment.remittanceInformation(),
                    payment.settlementDate(),
                    payment.createdAt(),
                    payment.settledAt());
        }
    }

    record PaymentPageResponse(List<PaymentResponse> items, String nextCursor) {}

    record MessageResponse(String direction, String type, String body, Instant at) {

        static MessageResponse from(PaymentMessages.Message message) {
            return new MessageResponse(message.direction(), message.type(), message.body(), message.at());
        }
    }
}
