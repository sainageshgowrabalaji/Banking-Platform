package com.sainagesh.bank.payments.adapter.out.screening;

import com.sainagesh.bank.payments.application.port.Screening;
import com.sainagesh.bank.payments.config.PaymentsProperties;
import com.sainagesh.bank.payments.domain.Payment;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * A stand-in for the compliance service. It stops a payment when the receiver's name matches a name
 * on a small blocked list from the settings.
 *
 * <p>Real screening compares names against government sanctions lists with fuzzy matching, and sends
 * unclear cases to a person. That arrives with the compliance service in phase 2. Because the saga
 * only knows the {@link Screening} port, swapping this class for a call to that service changes
 * nothing else.
 */
@Component
class LocalScreening implements Screening {

    private final List<String> blockedNames;

    LocalScreening(PaymentsProperties properties) {
        this.blockedNames = properties.screening().blockedNames().stream()
                .map(name -> name.toLowerCase(Locale.ROOT).trim())
                .filter(name -> !name.isEmpty())
                .toList();
    }

    @Override
    public Result screen(Payment payment) {
        String name = payment.creditor().name().toLowerCase(Locale.ROOT);
        for (String blocked : blockedNames) {
            if (name.contains(blocked)) {
                return Result.stopped("The receiver's name matches an entry on the blocked list");
            }
        }
        return Result.ok();
    }
}
