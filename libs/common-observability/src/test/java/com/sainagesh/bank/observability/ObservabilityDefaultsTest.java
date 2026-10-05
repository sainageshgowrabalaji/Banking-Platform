package com.sainagesh.bank.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityDefaultsTest {

    private final ObservabilityDefaults defaults = new ObservabilityDefaults();

    @Test
    void withNoCollectorNothingIsPushed() {
        MockEnvironment environment = new MockEnvironment();

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
        assertEquals("1.0", environment.getProperty("management.tracing.sampling.probability"));
    }

    @Test
    void theStandardVariableSwitchesMetricsOn() {
        MockEnvironment environment = new MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318");

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertNull(environment.getProperty("management.otlp.metrics.export.enabled"));
    }

    @Test
    void aValueTheServiceSetsItselfWins() {
        MockEnvironment environment = new MockEnvironment().withProperty("management.tracing.sampling.probability", "0.2");

        defaults.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("0.2", environment.getProperty("management.tracing.sampling.probability"));
    }

    @Test
    void onlyActuatorPathsAreLeftOutOfTraces() {
        assertTrue(ObservabilityAutoConfiguration.isActuator("/actuator/health"));
        assertTrue(ObservabilityAutoConfiguration.isActuator("/actuator"));
        assertFalse(ObservabilityAutoConfiguration.isActuator("/v1/payments"));
        assertFalse(ObservabilityAutoConfiguration.isActuator("/actuators"));
    }
}
