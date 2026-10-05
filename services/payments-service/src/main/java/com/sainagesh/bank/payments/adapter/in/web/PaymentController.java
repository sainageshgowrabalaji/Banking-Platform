package com.sainagesh.bank.payments.adapter.in.web;

import com.sainagesh.bank.idempotency.RequestHash;
import com.sainagesh.bank.payments.adapter.in.web.Dtos.CreatePaymentRequest;
import com.sainagesh.bank.payments.adapter.in.web.Dtos.MessageResponse;
import com.sainagesh.bank.payments.adapter.in.web.Dtos.PaymentPageResponse;
import com.sainagesh.bank.payments.adapter.in.web.Dtos.PaymentResponse;
import com.sainagesh.bank.payments.application.CancelPayment;
import com.sainagesh.bank.payments.application.CreatePayment;
import com.sainagesh.bank.payments.application.PaymentQueries;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.Party;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.iso20022.Pain001;
import com.sainagesh.bank.web.ApiException;
import com.sainagesh.bank.web.IdempotencyKey;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sending, tracking and cancelling payments. */
@RestController
@RequestMapping("/v1/payments")
class PaymentController {

    private final CreatePayment createPayment;
    private final CancelPayment cancelPayment;
    private final PaymentQueries queries;
    private final RequestHash requestHash;

    PaymentController(
            CreatePayment createPayment, CancelPayment cancelPayment, PaymentQueries queries, RequestHash requestHash) {
        this.createPayment = createPayment;
        this.cancelPayment = cancelPayment;
        this.queries = queries;
        this.requestHash = requestHash;
    }

    /**
     * Takes a payment as JSON. The answer is 202 Accepted, not 201 Created, because a payment is a
     * process. The body says how far it has come, and GET follows it from there.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PaymentResponse> create(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @Valid @RequestBody CreatePaymentRequest request) {
        IdempotencyKey key = IdempotencyKey.fromHeader(idempotencyKey);
        Payment payment = createPayment.create(
                new CreatePayment.Command(
                        request.debtorAccountId(),
                        request.creditor().toDomain(),
                        request.amount().toPositiveMoney(),
                        request.rail(),
                        request.endToEndId(),
                        request.remittanceInformation()),
                key,
                requestHash.of(request));
        return accepted(payment);
    }

    /**
     * Takes a payment as an ISO 20022 pain.001 message, the format a company's own accounting system
     * sends to its bank. It ends up as the same command as the JSON form.
     */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    ResponseEntity<PaymentResponse> createFromPain001(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey, @RequestBody String xml) {
        IdempotencyKey key = IdempotencyKey.fromHeader(idempotencyKey);
        Pain001 message = Pain001.parse(xml);
        UUID debtorAccountId;
        Rail rail;
        try {
            debtorAccountId = UUID.fromString(message.debtorAccount());
            rail = message.localInstrument() == null ? Rail.ACH : Rail.valueOf(message.localInstrument());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(
                    "INVALID_MESSAGE", "The debtor account must be an account id, and LclInstrm/Prtry one of BOOK, ACH, INSTANT");
        }
        Payment payment = createPayment.create(
                new CreatePayment.Command(
                        debtorAccountId,
                        new Party(message.creditorName(), message.creditorAccount(), message.creditorAgent()),
                        message.amount(),
                        rail,
                        message.endToEndId(),
                        message.remittanceInformation()),
                key,
                requestHash.of(xml));
        return accepted(payment);
    }

    private static ResponseEntity<PaymentResponse> accepted(Payment payment) {
        return ResponseEntity.accepted()
                .location(URI.create("/v1/payments/" + payment.id()))
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentId}")
    PaymentResponse get(@PathVariable UUID paymentId) {
        return PaymentResponse.from(queries.payment(paymentId));
    }

    @GetMapping
    PaymentPageResponse list(
            @RequestParam UUID accountId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        if (limit < 1 || limit > 200) {
            throw ApiException.badRequest("INVALID_PARAMETER", "limit must be from 1 to 200");
        }
        PaymentStore.Page page = queries.byAccount(accountId, status, limit, cursor);
        return new PaymentPageResponse(page.items().stream().map(PaymentResponse::from).toList(), page.nextCursor());
    }

    @PostMapping("/{paymentId}/cancel")
    PaymentResponse cancel(@PathVariable UUID paymentId, @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey) {
        IdempotencyKey.fromHeader(idempotencyKey);
        return PaymentResponse.from(cancelPayment.cancel(paymentId));
    }

    /** The ISO 20022 messages exchanged with the rail for this payment. */
    @GetMapping("/{paymentId}/messages")
    List<MessageResponse> messages(@PathVariable UUID paymentId) {
        return queries.messages(paymentId).stream().map(MessageResponse::from).toList();
    }
}
