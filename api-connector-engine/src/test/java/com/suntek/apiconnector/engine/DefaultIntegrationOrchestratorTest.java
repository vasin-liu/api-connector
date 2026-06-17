package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.AuthEngine;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.profile.NoneAuthProvider;
import com.suntek.apiconnector.auth.spi.AuthProvider;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.domain.model.ConnectorCode;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.HttpTransportRequest;
import com.suntek.apiconnector.engine.transport.HttpTransportResponse;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
            public void exchangeStream(HttpTransportRequest request, com.suntek.apiconnector.engine.transport.HttpStreamHandler handler) {
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
        assertTrue(result.authSnapshot().isPresent());
        assertTrue(result.authOutcome().isPresent());
    }

    /**
     * AUTH-05: mapping hook can read {@code accessToken} from snapshot without HTTP.
     */
    @Test
    void authSnapshotCarriesAccessTokenWithoutHttp() {
        ConnectorRegistry registry = new ConnectorRegistry();
        ConnectorSpec spec = new ConnectorSpec(
                "SNAP",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "snapshot_token"),
                List.of(new EndpointSpec("api", "GET", "/api", null, true)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null);
        registry.register(spec, Map.of());

        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(200, "{}", Map.of());
            }

            @Override
            public void exchangeStream(HttpTransportRequest request, com.suntek.apiconnector.engine.transport.HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };

        AuthEngine authEngine = new AuthEngine(List.of(new SnapshotTokenAuthProvider()));
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry, authEngine, transport, new ResponseEvaluator());

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("SNAP"),
                "api",
                InvocationRequest.HttpMethod.GET,
                "/api",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertTrue(result.authSnapshot().isPresent());
        assertEquals("snapshot-token-xyz", result.authSnapshot().orElseThrow().ext().get("accessToken"));
        assertTrue(result.authOutcome().orElseThrow().headers().containsKey("X-Snapshot-Auth"));
    }

    @Test
    void endpointAuthOverrideReplacesConnectorAuth() {
        Map<String, Object> connectorAuth = Map.of("type", "none");
        Map<String, Object> override = Map.of("type", "snapshot_token");
        ConnectorRegistry registry = new ConnectorRegistry();
        ConnectorSpec spec = new ConnectorSpec(
                "OVERRIDE",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                connectorAuth,
                List.of(new EndpointSpec("ep", "GET", "/ep", null, true, null, override)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null);
        registry.register(spec, Map.of());

        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(200, "{}", Map.of());
            }

            @Override
            public void exchangeStream(HttpTransportRequest request, com.suntek.apiconnector.engine.transport.HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };
        AuthEngine authEngine = new AuthEngine(List.of(new NoneAuthProvider(), new SnapshotTokenAuthProvider()));
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry, authEngine, transport, new ResponseEvaluator());

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("OVERRIDE"),
                "ep",
                InvocationRequest.HttpMethod.GET,
                "/ep",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertEquals("snapshot-token-xyz", result.authSnapshot().orElseThrow().ext().get("accessToken"));
        assertEquals(List.of("snapshot_token"), result.authSnapshot().orElseThrow().profileTypes());
    }

    private static final class SnapshotTokenAuthProvider implements AuthProvider {

        @Override
        public String profileType() {
            return "snapshot_token";
        }

        @Override
        public AuthOutcome apply(AuthContext context) {
            Map<String, Object> ext = context.ext();
            if (ext != null) {
                ext.put("accessToken", "snapshot-token-xyz");
            }
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Snapshot-Auth", "applied");
            return new AuthOutcome(headers, Map.of(), null);
        }
    }
}
