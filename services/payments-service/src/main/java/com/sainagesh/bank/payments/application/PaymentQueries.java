package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.payments.application.port.PaymentMessages;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read side. Nothing here changes anything. */
@Service
public class PaymentQueries {

    private final PaymentStore payments;
    private final PaymentMessages messages;

    public PaymentQueries(PaymentStore payments, PaymentMessages messages) {
        this.payments = payments;
        this.messages = messages;
    }

    public Payment payment(UUID paymentId) {
        return payments.find(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public PaymentStore.Page byAccount(UUID debtorAccountId, PaymentStatus status, int limit, String cursor) {
        return payments.listByAccount(debtorAccountId, status, limit, cursor);
    }

    /** The ISO 20022 messages exchanged with the rail for one payment, oldest first. */
    public List<PaymentMessages.Message> messages(UUID paymentId) {
        payment(paymentId);
        return messages.of(paymentId);
    }
}
