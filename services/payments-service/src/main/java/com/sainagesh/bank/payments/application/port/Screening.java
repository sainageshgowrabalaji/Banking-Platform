package com.sainagesh.bank.payments.application.port;

import com.sainagesh.bank.payments.domain.Payment;

/**
 * The checks a regulator expects before money leaves, such as a sanctions list check on the receiver.
 * In phase 2 the compliance service answers this. Until then a small local rule does.
 */
public interface Screening {

    Result screen(Payment payment);

    /** @param detail why the payment was stopped. Null when it is clear */
    record Result(boolean clear, String detail) {

        public static Result ok() {
            return new Result(true, null);
        }

        public static Result stopped(String detail) {
            return new Result(false, detail);
        }
    }
}
