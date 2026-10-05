package com.sainagesh.bank.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class OpenTelemetryLogAppenderTest {

    private final List<LogRecordData> sent = new ArrayList<>();
    private OpenTelemetrySdk openTelemetry;
    private LoggerContext logback;
    private Logger log;

    @BeforeEach
    void setUp() {
        LogRecordExporter collector = new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> logs) {
                sent.addAll(logs);
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setLoggerProvider(SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(collector))
                        .build())
                .build();

        // A Logback of its own, so the test does not touch the logging of the build.
        logback = new LoggerContext();
        logback.setMDCAdapter(MDC.getMDCAdapter());
        OpenTelemetryLogAppender appender = new OpenTelemetryLogAppender(openTelemetry);
        appender.setContext(logback);
        appender.start();
        log = logback.getLogger("com.sainagesh.bank.payments.Demo");
        log.setLevel(Level.DEBUG);
        log.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        logback.stop();
        openTelemetry.close();
    }

    @Test
    void aLineWrittenInsideASpanCarriesItsTraceId() {
        Span span = openTelemetry.getTracer("test").spanBuilder("pay").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            log.info("Payment {} settled", "p-1");
        } finally {
            span.end();
        }

        LogRecordData record = sent.get(0);
        assertEquals("Payment p-1 settled", record.getBodyValue().asString());
        assertEquals(Severity.INFO, record.getSeverity());
        assertEquals("com.sainagesh.bank.payments.Demo", record.getInstrumentationScopeInfo().getName());
        assertEquals(span.getSpanContext().getTraceId(), record.getSpanContext().getTraceId());
        assertEquals(span.getSpanContext().getSpanId(), record.getSpanContext().getSpanId());
    }

    @Test
    void anExceptionTravelsWithItsStackTrace() {
        log.error("Could not reach the ledger", new IllegalStateException("connection refused"));

        LogRecordData record = sent.get(0);
        assertEquals(Severity.ERROR, record.getSeverity());
        assertEquals(
                "java.lang.IllegalStateException", record.getAttributes().get(AttributeKey.stringKey("exception.type")));
        assertEquals("connection refused", record.getAttributes().get(AttributeKey.stringKey("exception.message")));
        assertTrue(record.getAttributes()
                .get(AttributeKey.stringKey("exception.stacktrace"))
                .contains("anExceptionTravelsWithItsStackTrace"));
    }

    @Test
    void valuesInTheLoggingContextBecomeAttributes() {
        MDC.put("paymentId", "p-7");

        log.warn("Retrying");

        assertEquals("p-7", sent.get(0).getAttributes().get(AttributeKey.stringKey("paymentId")));
    }

    @Test
    void openTelemetrysOwnLinesAreLeftOut() {
        Logger own = logback.getLogger("io.opentelemetry.exporter.Demo");
        own.addAppender(log.iteratorForAppenders().next());

        own.warn("Export failed");

        assertTrue(sent.isEmpty());
    }
}
