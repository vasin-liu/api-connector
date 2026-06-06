package com.suntek.integration.engine;

import com.suntek.integration.auth.AuthEngine;
import com.suntek.integration.auth.profile.NoneAuthProvider;
import com.suntek.integration.domain.model.ConnectorCode;
import com.suntek.integration.domain.model.InvocationRequest;
import com.suntek.integration.engine.transport.HttpTransport;
import com.suntek.integration.engine.transport.HttpTransportRequest;
import com.suntek.integration.engine.transport.HttpTransportResponse;
import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointSpec;
import com.suntek.integration.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultIntegrationOrchestratorTest {

    @Test
    void parsesVendorFieldsFromJsonBody() {
        ConnectorRegistry registry = new ConnectorRegistry();
        ConnectorSpec spec = new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("echo", "GET", "/echo", null, true)),
                new ResponseSpec("$.success == true", "$.obj", "$.msg", "$.code"),
                null,
                null);
        registry.register(spec, Map.of());

        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(
                        200,
                        "{\"success\":true,\"msg\":\"ok\",\"obj\":{\"k\":1},\"code\":0}",
                        Map.of());
            }

            @Override
            public void exchangeStream(HttpTransportRequest request, com.suntek.integration.engine.transport.HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };

        AuthEngine authEngine = new AuthEngine(List.of(new NoneAuthProvider()));
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry, authEngine, transport, new ResponseEvaluator());

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("TEST"),
                "echo",
                InvocationRequest.HttpMethod.GET,
                "/echo",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertTrue(result.success());
        assertEquals("0", result.vendorCode());
        assertEquals("ok", result.vendorMessage());
        assertInstanceOf(Map.class, result.parsedData());
    }
}
