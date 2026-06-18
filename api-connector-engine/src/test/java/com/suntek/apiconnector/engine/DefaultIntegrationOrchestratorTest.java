package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.AuthEngine;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.profile.NoneAuthProvider;
import com.suntek.apiconnector.auth.spi.AuthProvider;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.domain.model.ConnectorCode;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.HttpTransportRequest;
import com.suntek.apiconnector.engine.transport.HttpTransportResponse;
import com.suntek.apiconnector.mapping.ErrorMappingTrigger;
import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.mapping.TransformPipeline;
import com.suntek.apiconnector.mapping.TransformStepRegistry;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * MAP-06 / D-01: the body handed to auth (and thus signed) is the post-mapRequest body.
     */
    @Test
    void requestBodyIsMappedBeforeAuthSigns() {
        ConnectorRegistry registry = new ConnectorRegistry();
        MappingSpec mapping = new MappingSpec(
                new DirectionMappingSpec(
                        List.of(new MappingRule("rename", "$.a", "$.b", null, null, null)),
                        null),
                null,
                null);
        ConnectorSpec spec = new ConnectorSpec(
                "MAPREQ",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("echo", "POST", "/echo", null, true)),
                new ResponseSpec(null, null, null, null),
                null,
                mapping,
                null);
        registry.register(spec, Map.of());

        RecordingMappingEngine mappingEngine = new RecordingMappingEngine();
        mappingEngine.requestResult = "{\"b\":1}";
        RecordingNoneAuthProvider authProvider = new RecordingNoneAuthProvider();
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry,
                new AuthEngine(List.of(authProvider)),
                okTransport("{}"),
                new ResponseEvaluator(),
                mappingEngine,
                new TransformPipeline(new TransformStepRegistry(List.of())),
                new ResolvedMappingCache(),
                true);

        orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("MAPREQ"),
                "echo",
                InvocationRequest.HttpMethod.POST,
                "/echo",
                Map.of(),
                Map.of(),
                "{\"a\":1}",
                InvocationRequest.InvocationMode.SYNC));

        assertEquals(1, mappingEngine.mapRequestCalls.get());
        assertEquals("{\"b\":1}", authProvider.signedBody);
    }

    /**
     * D-04: passthrough endpoint (hasAnyMapping == false) never touches the mapping engine.
     */
    @Test
    void passthroughSkipsMappingEngine() {
        ConnectorRegistry registry = new ConnectorRegistry();
        ConnectorSpec spec = new ConnectorSpec(
                "PASS",
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

        RecordingMappingEngine mappingEngine = new RecordingMappingEngine();
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry,
                new AuthEngine(List.of(new NoneAuthProvider())),
                okTransport("{}"),
                new ResponseEvaluator(),
                mappingEngine,
                new TransformPipeline(new TransformStepRegistry(List.of())),
                new ResolvedMappingCache(),
                true);

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("PASS"),
                "echo",
                InvocationRequest.HttpMethod.GET,
                "/echo",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertTrue(result.success());
        assertEquals(0, mappingEngine.mapRequestCalls.get());
        assertEquals(0, mappingEngine.mapResponseCalls.get());
        assertEquals(0, mappingEngine.mapErrorCalls.get());
    }

    /**
     * D-02/D-03: response transform (decrypt) runs before evaluate; success routes to mapResponse.
     */
    @Test
    void responseTransformRunsBeforeEvaluateAndMapsSuccess() {
        ConnectorRegistry registry = new ConnectorRegistry();
        MappingSpec mapping = new MappingSpec(
                null,
                new DirectionMappingSpec(
                        List.of(new MappingRule("set", null, "$.mapped", "yes", null, null)),
                        null),
                null);
        ConnectorSpec spec = new ConnectorSpec(
                "RESPMAP",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("api", "GET", "/api", null, true)),
                new ResponseSpec("$.ok == true", "$", null, null),
                null,
                mapping,
                List.of(Map.of("type", "marker_response", "direction", "response")));
        registry.register(spec, Map.of());

        RecordingMappingEngine mappingEngine = new RecordingMappingEngine();
        mappingEngine.responseResult = "{\"mapped\":\"yes\"}";
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry,
                new AuthEngine(List.of(new NoneAuthProvider())),
                okTransport("{\"ok\":false}"),
                new ResponseEvaluator(),
                mappingEngine,
                new TransformPipeline(new TransformStepRegistry(List.of(new MarkerResponseTransformStep()))),
                new ResolvedMappingCache(),
                true);

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("RESPMAP"),
                "api",
                InvocationRequest.HttpMethod.GET,
                "/api",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertTrue(result.success());
        assertEquals(1, mappingEngine.mapResponseCalls.get());
        assertEquals(0, mappingEngine.mapErrorCalls.get());
        assertEquals("{\"ok\":true,\"stage\":\"decoded\"}", mappingEngine.lastResponseBody);
        assertEquals("{\"mapped\":\"yes\"}", result.rawBody());
    }

    /**
     * D-03: non-2xx / business failure routes to mapError via {@code shouldMapError}.
     */
    @Test
    void responseErrorRoutesToMapError() {
        ConnectorRegistry registry = new ConnectorRegistry();
        MappingSpec mapping = new MappingSpec(
                null,
                null,
                new DirectionMappingSpec(
                        List.of(new MappingRule("set", null, "$.err", "1", null, null)),
                        null));
        ConnectorSpec spec = new ConnectorSpec(
                "ERRMAP",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("api", "GET", "/api", null, true)),
                new ResponseSpec("$.ok == true", "$", null, null),
                null,
                mapping,
                null);
        registry.register(spec, Map.of());

        RecordingMappingEngine mappingEngine = new RecordingMappingEngine();
        mappingEngine.errorResult = "{\"err\":true}";
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(500, "{\"ok\":false}", Map.of());
            }

            @Override
            public void exchangeStream(HttpTransportRequest request, com.suntek.apiconnector.engine.transport.HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };
        DefaultIntegrationOrchestrator orchestrator = new DefaultIntegrationOrchestrator(
                registry,
                new AuthEngine(List.of(new NoneAuthProvider())),
                transport,
                new ResponseEvaluator(),
                mappingEngine,
                new TransformPipeline(new TransformStepRegistry(List.of())),
                new ResolvedMappingCache(),
                true);

        var result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode("ERRMAP"),
                "api",
                InvocationRequest.HttpMethod.GET,
                "/api",
                Map.of(),
                Map.of(),
                null,
                InvocationRequest.InvocationMode.SYNC));

        assertFalse(result.success());
        assertEquals(1, mappingEngine.mapErrorCalls.get());
        assertEquals(0, mappingEngine.mapResponseCalls.get());
        assertEquals("{\"err\":true}", result.rawBody());
    }

    private static HttpTransport okTransport(String body) {
        return new HttpTransport() {
            @Override
            public HttpTransportResponse exchange(HttpTransportRequest request) {
                return new HttpTransportResponse(200, body, Map.of());
            }

            @Override
            public void exchangeStream(HttpTransportRequest request, com.suntek.apiconnector.engine.transport.HttpStreamHandler handler) {
                throw new UnsupportedOperationException("not used in test");
            }
        };
    }

    private static final class RecordingMappingEngine implements MappingEngine {

        private final AtomicInteger mapRequestCalls = new AtomicInteger();
        private final AtomicInteger mapResponseCalls = new AtomicInteger();
        private final AtomicInteger mapErrorCalls = new AtomicInteger();
        private volatile String lastResponseBody;
        private String requestResult;
        private String responseResult;
        private String errorResult;

        @Override
        public String mapRequest(MappingContext ctx, ResolvedMapping config) {
            mapRequestCalls.incrementAndGet();
            return requestResult != null ? requestResult : ctx.rawBody();
        }

        @Override
        public String mapResponse(MappingContext ctx, ResolvedMapping config) {
            mapResponseCalls.incrementAndGet();
            lastResponseBody = ctx.rawBody();
            return responseResult != null ? responseResult : ctx.rawBody();
        }

        @Override
        public String mapError(MappingContext ctx, ResolvedMapping config, ErrorMappingTrigger trigger) {
            mapErrorCalls.incrementAndGet();
            lastResponseBody = ctx.rawBody();
            return errorResult != null ? errorResult : ctx.rawBody();
        }
    }

    private static final class RecordingNoneAuthProvider implements AuthProvider {

        private volatile String signedBody;

        @Override
        public String profileType() {
            return "none";
        }

        @Override
        public AuthOutcome apply(AuthContext context) {
            signedBody = context.body();
            return new AuthOutcome(Map.of(), Map.of(), null);
        }
    }

    private static final class MarkerResponseTransformStep implements com.suntek.apiconnector.mapping.spi.TransformStep {

        @Override
        public String type() {
            return "marker_response";
        }

        @Override
        public String apply(com.suntek.apiconnector.mapping.TransformContext ctx) {
            return "{\"ok\":true,\"stage\":\"decoded\"}";
        }
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
