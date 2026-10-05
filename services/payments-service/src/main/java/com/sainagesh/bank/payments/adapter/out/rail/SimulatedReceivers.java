package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.domain.ReasonCode;
import java.util.Optional;

/**
 * The made-up world outside this bank. A real network would ask the receiving bank whether an account
 * exists and is open. The simulators decide by the last four digits of the account number, so a demo
 * or a test can ask for any outcome it wants.
 *
 * <ul>
 *   <li>ends in 0000, the account is closed
 *   <li>ends in 1111, there is no such account
 *   <li>ends in 2222, the account is blocked
 *   <li>anything else is accepted
 * </ul>
 */
final class SimulatedReceivers {

    private SimulatedReceivers() {}

    static Optional<ReasonCode> problemWith(String accountNumber) {
        if (accountNumber.endsWith("0000")) {
            return Optional.of(ReasonCode.AC04);
        }
        if (accountNumber.endsWith("1111")) {
            return Optional.of(ReasonCode.AC01);
        }
        if (accountNumber.endsWith("2222")) {
            return Optional.of(ReasonCode.AC06);
        }
        return Optional.empty();
    }
}
