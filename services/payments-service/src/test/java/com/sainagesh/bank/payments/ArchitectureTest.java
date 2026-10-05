package com.sainagesh.bank.payments;

import com.sainagesh.bank.testing.HexagonalRules;
import org.junit.jupiter.api.Test;

/** Fails the build when code breaks the layering described in docs/adr/0002. */
class ArchitectureTest {

    private final HexagonalRules rules = HexagonalRules.of("com.sainagesh.bank.payments");

    @Test
    void theDomainIsPlainJava() {
        rules.domainIsPlainJava();
        rules.domainDoesNotKnowTheOuterLayers();
    }

    @Test
    void useCasesOnlyKnowTheDomainAndTheirPorts() {
        rules.useCasesDoNotKnowTheAdapters();
        rules.useCasesDoNotKnowWebOrDatabaseTypes();
    }

    @Test
    void controllersAndOutboundAdaptersNeverTouchEachOther() {
        rules.inboundAndOutboundAdaptersAreStrangers();
    }
}
