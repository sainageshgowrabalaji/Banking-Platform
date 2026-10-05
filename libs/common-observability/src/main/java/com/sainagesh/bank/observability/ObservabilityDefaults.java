package com.sainagesh.bank.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Sets the starting values for tracing and metrics, before the service starts.
 *
 * <p>They go in at the lowest priority, so a value in {@code application.yml}, a command line
 * argument or an environment variable always wins.
 *
 * <p>The one switch that matters is the standard OpenTelemetry variable
 * {@code OTEL_EXPORTER_OTLP_ENDPOINT}. Spring Boot reads it and sends traces, metrics and logs to that
 * collector. With no collector address, nothing is pushed anywhere. Trace ids still show in the log
 * lines, and Prometheus can still read {@code /actuator/prometheus}.
 */
public class ObservabilityDefaults implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "bankObservabilityDefaults";

    /** Where Spring Boot looks for the collector address, in the order it would use them. */
    private static final String[] COLLECTOR_SETTINGS = {
        "management.otlp.metrics.export.url", "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT", "OTEL_EXPORTER_OTLP_ENDPOINT"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();

        // Record every request. Spring Boot's own default keeps one in ten, which suits a busy
        // production system. Lower it there with OTEL_TRACES_SAMPLER_ARG.
        defaults.put("management.tracing.sampling.probability", "1.0");

        // Leave out what would only be noise in a trace view. A job that polls every second would
        // otherwise make a new trace each time, and Spring Security adds four spans to every request.
        defaults.put("management.observations.enable.tasks.scheduled.execution", "false");
        defaults.put("management.observations.enable.spring.security", "false");

        // The OTLP metrics pusher would otherwise try localhost:4318 every minute and log a
        // warning each time nothing answers.
        if (!hasCollector(environment)) {
            defaults.put("management.otlp.metrics.export.enabled", "false");
        }

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }

    private static boolean hasCollector(ConfigurableEnvironment environment) {
        for (String name : COLLECTOR_SETTINGS) {
            String value = environment.getProperty(name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
