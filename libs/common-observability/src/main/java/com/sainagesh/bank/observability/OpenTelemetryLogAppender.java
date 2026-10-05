package com.sainagesh.bank.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Logback appender that hands each log line to OpenTelemetry.
 *
 * <p>Logback calls {@link #append} on the thread that wrote the line. The request's span is still
 * active on that thread, so the record picks up its trace id and span id from the current context.
 * OpenTelemetry then batches the records and sends them to the collector in the background.
 *
 * <p>It uses only the stable part of the OpenTelemetry API, so it keeps working when Spring Boot
 * moves to a newer OpenTelemetry.
 */
final class OpenTelemetryLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final AttributeKey<String> THREAD_NAME = AttributeKey.stringKey("thread.name");
    private static final AttributeKey<String> EXCEPTION_TYPE = AttributeKey.stringKey("exception.type");
    private static final AttributeKey<String> EXCEPTION_MESSAGE = AttributeKey.stringKey("exception.message");
    private static final AttributeKey<String> EXCEPTION_STACKTRACE = AttributeKey.stringKey("exception.stacktrace");

    /** OpenTelemetry's own classes log too. Sending those lines back to it could loop. */
    private static final String OWN_LOGGERS = "io.opentelemetry";

    private final OpenTelemetry openTelemetry;

    OpenTelemetryLogAppender(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLoggerName().startsWith(OWN_LOGGERS)) {
            return;
        }
        LogRecordBuilder record = openTelemetry
                .getLogsBridge()
                .loggerBuilder(event.getLoggerName())
                .build()
                .logRecordBuilder()
                .setTimestamp(event.getTimeStamp(), TimeUnit.MILLISECONDS)
                .setContext(Context.current())
                .setSeverity(severity(event.getLevel()))
                .setSeverityText(event.getLevel().levelStr)
                .setBody(event.getFormattedMessage())
                .setAttribute(THREAD_NAME, event.getThreadName());

        // Values a service put in the logging context, such as a payment id.
        for (Map.Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
            if (entry.getValue() != null) {
                record.setAttribute(AttributeKey.stringKey(entry.getKey()), entry.getValue());
            }
        }

        IThrowableProxy thrown = event.getThrowableProxy();
        if (thrown != null) {
            record.setAttribute(EXCEPTION_TYPE, thrown.getClassName());
            if (thrown.getMessage() != null) {
                record.setAttribute(EXCEPTION_MESSAGE, thrown.getMessage());
            }
            record.setAttribute(EXCEPTION_STACKTRACE, ThrowableProxyUtil.asString(thrown));
        }
        record.emit();
    }

    static Severity severity(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> Severity.ERROR;
            case Level.WARN_INT -> Severity.WARN;
            case Level.INFO_INT -> Severity.INFO;
            case Level.DEBUG_INT -> Severity.DEBUG;
            case Level.TRACE_INT -> Severity.TRACE;
            default -> Severity.UNDEFINED_SEVERITY_NUMBER;
        };
    }
}
