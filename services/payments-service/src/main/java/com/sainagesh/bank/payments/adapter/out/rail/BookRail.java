package com.sainagesh.bank.payments.adapter.out.rail;

import com.sainagesh.bank.payments.application.port.RailGateway;
import com.sainagesh.bank.payments.domain.BusinessCalendar;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * The book rail. Both accounts are in this bank, so there is no network to talk to. The payment is
 * "sent" and "settled" the moment it is asked for, and the ledger entry does the real work.
 */
@Component
class BookRail implements RailGateway {

    private final Clock clock;

    BookRail(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Rail rail() {
        return Rail.BOOK;
    }

    @Override
    public Submission submit(Payment payment) {
        return new Submission.Sent("BOOK-" + payment.id(), clock.instant().atZone(BusinessCalendar.EASTERN).toLocalDate());
    }

    /** Both accounts are in this bank's ledger. Nothing has moved until the entry is posted. */
    @Override
    public boolean leavesTheBank() {
        return false;
    }

    @Override
    public Outcome outcome(Payment payment) {
        return new Outcome.Settled();
    }
}
