package com.sainagesh.bank.payments.adapter.out.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.money.Money;
import com.sainagesh.bank.payments.domain.ReasonCode;
import com.sainagesh.bank.payments.iso20022.Pacs002;
import com.sainagesh.bank.payments.iso20022.Pacs008;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SimulatorsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");
    private final InstantNetworkSimulator network = new InstantNetworkSimulator(Clock.fixed(NOW, ZoneOffset.UTC));

    private static String pacs008(String transactionId, String creditorAccount) {
        return new Pacs008(
                        "MSG-" + transactionId,
                        NOW,
                        transactionId,
                        "INV-1",
                        transactionId,
                        Money.of("10.00", "USD"),
                        LocalDate.of(2026, 10, 5),
                        "Account holder",
                        "debtor",
                        "999000017",
                        "Jane Doe",
                        creditorAccount,
                        "021000021",
                        null)
                .toXml();
    }

    @Test
    void theLastFourDigitsOfTheAccountDecideTheOutcome() {
        assertEquals(Optional.empty(), SimulatedReceivers.problemWith("123456789"));
        assertEquals(Optional.of(ReasonCode.AC04), SimulatedReceivers.problemWith("12340000"));
        assertEquals(Optional.of(ReasonCode.AC01), SimulatedReceivers.problemWith("12341111"));
        assertEquals(Optional.of(ReasonCode.AC06), SimulatedReceivers.problemWith("12342222"));
    }

    @Test
    void theInstantNetworkSettlesAGoodPayment() {
        Pacs002 answer = Pacs002.parse(network.send(pacs008("TX-1", "123456789")));

        assertTrue(answer.settled());
        assertEquals("ACSC", answer.status());
        assertEquals("TX-1", answer.originalTransactionId());
        assertEquals("MSG-TX-1", answer.originalMessageId());
    }

    @Test
    void theInstantNetworkRejectsAClosedAccountWithItsReason() {
        Pacs002 answer = Pacs002.parse(network.send(pacs008("TX-2", "99990000")));

        assertFalse(answer.settled());
        assertEquals("RJCT", answer.status());
        assertEquals("AC04", answer.reasonCode());
    }

    @Test
    void sendingTheSamePaymentTwiceGetsTheSameAnswerAndIsNotPaidTwice() {
        String first = network.send(pacs008("TX-3", "123456789"));
        String again = network.send(pacs008("TX-3", "123456789"));

        assertEquals(first, again);
    }

    @Test
    void achReturnCodesBecomeIsoReasons() {
        assertEquals(ReasonCode.AC04, AchRail.fromReturnCode("R02"));
        assertEquals(ReasonCode.AC01, AchRail.fromReturnCode("R03"));
        assertEquals(ReasonCode.AC06, AchRail.fromReturnCode("R16"));
        assertEquals(ReasonCode.MS03, AchRail.fromReturnCode("R99"));
        assertEquals(ReasonCode.MS03, AchRail.fromReturnCode(null));
    }
}
