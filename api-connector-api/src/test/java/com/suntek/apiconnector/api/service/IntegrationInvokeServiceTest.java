package com.suntek.apiconnector.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.suntek.apiconnector.api.config.IntegrationInvokeProperties;
import com.suntek.apiconnector.api.dto.EndpointInvokeRequest;
import com.suntek.apiconnector.api.invoke.InvokeAuditLogger;
import com.suntek.apiconnector.api.invoke.InvokeRateLimiter;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IntegrationInvokeServiceTest {

    private static final String MDC_REQUEST_ID = "requestId";

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static ConnectorRegistry registryWithEcho() {
        ConnectorRegistry registry = new ConnectorRegistry();
        ConnectorSpec spec = new ConnectorSpec(
                "SVC",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("echo", "GET", "/echo", null, true)),
                new ResponseSpec(null, null, null, null),
                null,
                null,
                null);
        registry.register(spec, Map.of());
        return registry;
    }

    private static InvokeRateLimiter openRateLimiter() {
        return new InvokeRateLimiter();
    }

    // ---- D-10: correlation-id precedence ----

    @Test
    void resolveCorrelationIdReturnsRequestIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "rid-abc");
        request.addHeader("X-Trace-Id", "tid-xyz");
        bindRequest(request);

        assertThat(IntegrationInvokeService.resolveCorrelationId()).isEqualTo("rid-abc");
    }

    @Test
    void resolveCorrelationIdFallsBackToTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "tid-xyz");
        bindRequest(request);

        assertThat(IntegrationInvokeService.resolveCorrelationId()).isEqualTo("tid-xyz");
    }

    @Test
    void resolveCorrelationIdGeneratesUuidWhenAbsent() {
        bindRequest(new MockHttpServletRequest());

        String id = IntegrationInvokeService.resolveCorrelationId();
        assertThat(id).isNotBlank();
        assertThat(id).matches("[0-9a-fA-F-]{36}");
    }

    // ---- Security T-03-03: sanitize CR/LF + length cap ----

    @Test
    void resolveCorrelationIdSanitizesCrLf() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc\r\ndef\r\ninjected=line");
        bindRequest(request);

        String id = IntegrationInvokeService.resolveCorrelationId();
        assertThat(id).doesNotContain("\r");
        assertThat(id).doesNotContain("\n");
    }

    @Test
    void sanitizeCorrelationIdCapsLength() {
        String raw = "x".repeat(300);
        String sanitized = IntegrationInvokeService.sanitizeCorrelationId(raw);
        assertThat(sanitized.length()).isLessThanOrEqualTo(128);
    }

    // ---- D-11: MDC put at entry / remove in finally ----

    @Test
    void invokeSetsAndClearsMdcRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "mdc-test");
        bindRequest(request);

        String[] captured = new String[1];
        IntegrationOrchestrator orchestrator = new IntegrationOrchestrator() {
            @Override
            public InvocationResult invoke(InvocationRequest req) {
                captured[0] = MDC.get(MDC_REQUEST_ID);
                return new InvocationResult(200, true, "0", "ok", "{}", null, 5L, Map.of());
            }

            @Override
            public void invokeStream(InvocationRequest req, StreamingInvocationSink sink) {
                throw new UnsupportedOperationException("not used");
            }
        };
        IntegrationInvokeService service = new IntegrationInvokeService(
                registryWithEcho(), orchestrator, openRateLimiter(),
                new InvokeAuditLogger(), new IntegrationInvokeProperties());

        service.invokeEndpoint("SVC", "echo", new EndpointInvokeRequest());

        assertThat(captured[0]).isEqualTo("mdc-test");
        assertThat(MDC.get(MDC_REQUEST_ID)).isNull();
    }

    // ---- D-13/D-14 + SC#4 (streaming): stream audit carries captured requestId + outcome ----

    @Test
    void streamAuditLineCarriesRequestIdAndSuccessOutcome() {
        String message = captureStreamAudit("stream-ok", true);
        assertThat(message).contains("requestId=stream-ok");
        assertThat(message).contains("outcome=SUCCESS");
        assertThat(message).contains("context=RUNTIME_STREAM");
        assertThat(message).doesNotContain("requestId=-");
    }

    @Test
    void streamAuditLineCarriesVendorErrorOutcomeOnFail() {
        String message = captureStreamAudit("stream-bad", false);
        assertThat(message).contains("requestId=stream-bad");
        assertThat(message).contains("outcome=VENDOR_ERROR");
        assertThat(message).contains("context=RUNTIME_STREAM");
        assertThat(message).doesNotContain("requestId=-");
    }

    private String captureStreamAudit(String requestId, boolean completeNormally) {
        Logger audit = (Logger) LoggerFactory.getLogger("integration.invoke.audit");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        audit.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-Id", requestId);
            bindRequest(request);

            IntegrationOrchestrator orchestrator = new IntegrationOrchestrator() {
                @Override
                public InvocationResult invoke(InvocationRequest req) {
                    throw new UnsupportedOperationException("not used");
                }

                @Override
                public void invokeStream(InvocationRequest req, StreamingInvocationSink sink) {
                    if (completeNormally) {
                        sink.complete();
                    } else {
                        sink.fail(new RuntimeException("vendor stream failed"));
                    }
                }
            };
            IntegrationInvokeService service = new IntegrationInvokeService(
                    registryWithEcho(), orchestrator, openRateLimiter(),
                    new InvokeAuditLogger(), new IntegrationInvokeProperties());

            service.streamEndpoint("SVC", "echo", new EndpointInvokeRequest(), new StreamingInvocationSink() {
                @Override
                public void writeLine(String line) {
                }
            });

            assertThat(appender.list).hasSize(1);
            return appender.list.get(0).getFormattedMessage();
        } finally {
            audit.detachAppender(appender);
            appender.stop();
        }
    }
}
