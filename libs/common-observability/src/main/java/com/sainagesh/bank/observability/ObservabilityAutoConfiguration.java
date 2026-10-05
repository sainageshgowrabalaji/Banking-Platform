package com.sainagesh.bank.observability;

import ch.qos.logback.classic.LoggerContext;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Spring Boot already sets up tracing and metrics once the OpenTelemetry starter is on the classpath.
 * Two pieces are added here. The bridge from Logback to OpenTelemetry, which Spring Boot leaves out,
 * and a rule that keeps health checks out of the traces.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnClass({OpenTelemetry.class, LoggerContext.class})
public class ObservabilityAutoConfiguration {

    /** Only when a collector address for logs is set. Without one the lines would go nowhere. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
    LogExport logExport(OpenTelemetry openTelemetry) {
        return new LogExport(openTelemetry);
    }

    /** Health checks and metric scrapes arrive every few seconds. They are not worth a trace each. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ServerRequestObservationContext.class, HttpServletRequest.class})
    static class WebConfiguration {

        @Bean
        ObservationPredicate leaveOutActuatorRequests() {
            return (name, context) -> !(context instanceof ServerRequestObservationContext request)
                    || !isActuator(request.getCarrier().getRequestURI());
        }
    }

    static boolean isActuator(String path) {
        return path != null && (path.equals("/actuator") || path.startsWith("/actuator/"));
    }
}
