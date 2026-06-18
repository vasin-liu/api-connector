package com.suntek.apiconnector.api.invoke;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class InvokeAuditLoggerTest {

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        auditLogger = (Logger) LoggerFactory.getLogger("integration.invoke.audit");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void auditLineIncludesRequestIdAndOutcome() {
        new InvokeAuditLogger().log(new InvokeAuditEvent(
                "TESTC",
                "echo",
                "GET",
                "/echo",
                true,
                200,
                12L,
                "127.0.0.1",
                "RUNTIME",
                "req-123",
                "SUCCESS"));

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("requestId=req-123");
        assertThat(message).contains("outcome=SUCCESS");
    }
}
