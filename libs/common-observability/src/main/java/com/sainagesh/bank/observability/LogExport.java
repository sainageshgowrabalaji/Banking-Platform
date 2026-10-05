package com.sainagesh.bank.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Sends every log line to the collector as well as to the console.
 *
 * <p>It adds one appender to the Logback root logger when the service starts and takes it off again
 * when the service stops. In Grafana this is what lets you jump from a slow trace to the log lines
 * written during it.
 */
class LogExport implements InitializingBean, DisposableBean {

    static final String APPENDER_NAME = "OPEN_TELEMETRY";

    private final OpenTelemetry openTelemetry;

    LogExport(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        Logger root = rootLogger();
        if (root == null || root.getAppender(APPENDER_NAME) != null) {
            return;
        }
        OpenTelemetryLogAppender appender = new OpenTelemetryLogAppender(openTelemetry);
        appender.setName(APPENDER_NAME);
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
    }

    @Override
    public void destroy() {
        Logger root = rootLogger();
        if (root != null) {
            root.detachAppender(APPENDER_NAME);
        }
    }

    /** Null when the service logs through something other than Logback. */
    private static Logger rootLogger() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        return factory instanceof LoggerContext context ? context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) : null;
    }
}
