package com.suntek.apiconnector.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyCompatIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ConnectorRegistry registry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.security.enabled", () -> "false");
        registry.add("integration.persistence.source", () -> "memory");
        registry.add("integration.persistence.sync-on-startup", () -> "false");
        registry.add("integration.legacy.enabled", () -> "true");
    }

    @BeforeEach
    void rebindIdpsToWireMock() {
        wireMock.stubFor(get(urlEqualTo("/brain-auth/ping"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"msg\":\"ok\",\"obj\":{\"p\":1},\"code\":\"0\"}")));
        ConnectorSpec idps = registry.require("IDPS");
        ConnectorSpec rebound = new ConnectorSpec(
                idps.code3rd(),
                idps.version(),
                "http://localhost:" + wireMock.getPort(),
                idps.protocol(),
                java.util.Map.of("type", "none"),
                idps.endpoints(),
                idps.response(),
                idps.transport(),
                idps.mapping(),
                idps.transform());
        registry.save(rebound, null, ConnectorSpecStatus.PUBLISHED);
    }

    @Test
    void legacyIdpsGetProxiesVendorJson() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/idps/brain-auth/ping"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("\"obj\""));
    }

    /**
     * PIPE-02 / ROADMAP SC#2 (D-06/D-08): a legacy thirdpart URL and the unified API hit the SAME
     * {@code orchestrator.invoke()} for an equivalent endpoint and produce the same CORE result —
     * here the response-mapped body ({@code raw} renamed to {@code mapped}). The outer envelope is
     * allowed to differ (unified wraps in {@code ProxyInvokeResponse}; legacy returns the raw mapped
     * vendor body), so byte-identity of the envelope is NOT asserted (D-08).
     */
    @Test
    void legacyAndUnifiedCoreResultParity() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/vendor/echo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"raw\":\"hello\",\"ok\":true}")));
        registerMappedIdps(wireMock.getPort());

        // (a) unified API: by endpointId -> orchestrator applies request+response mapping.
        HttpResponse<String> unified = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port
                                + "/api/v1/integrations/IDPS/endpoints/parity/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"{\\\"a\\\":1}\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // (b) legacy thirdpart URL: same connector + endpoint via the shared LEGACY invoke path.
        HttpResponse<String> legacy = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/idps/vendor/echo"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"a\":1}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, unified.statusCode());
        assertEquals(200, legacy.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode unifiedEnvelope = mapper.readTree(unified.body());
        // Unified envelope core result.
        assertTrue(unifiedEnvelope.get("success").asBoolean(), "unified business success");
        assertTrue(unifiedEnvelope.get("vendorCode").isNull(), "no vendor error code");

        // Core mapped body parity: unified.rawBody (the mapped finalBody) == legacy raw mapped body.
        JsonNode unifiedMapped = mapper.readTree(unifiedEnvelope.get("rawBody").asText());
        JsonNode legacyMapped = mapper.readTree(legacy.body());
        assertEquals(legacyMapped, unifiedMapped,
                "legacy and unified must share the same mapped core body (same orchestrator pipeline)");
        // Both ran response mapping (raw -> mapped) through the shared pipeline.
        assertTrue(legacyMapped.has("mapped"), "legacy body carries the mapped field");
        assertFalse(legacyMapped.has("raw"), "legacy body must not carry the pre-mapping field");
        assertEquals("hello", legacyMapped.get("mapped").asText());
    }

    /**
     * Rebinds IDPS to WireMock with a single {@code parity} endpoint carrying request mapping
     * ({@code a} -> {@code b}) and response mapping ({@code raw} -> {@code mapped}), so both the
     * unified API and the legacy {@code /idps/...} URL exercise mapping through the shared pipeline.
     */
    private void registerMappedIdps(int wireMockPort) {
        ConnectorSpec idps = registry.require("IDPS");
        ConnectorSpec mapped = new ConnectorSpec(
                idps.code3rd(),
                idps.version(),
                "http://localhost:" + wireMockPort,
                idps.protocol(),
                Map.of("type", "none"),
                List.of(new EndpointSpec("parity", "POST", "/vendor/echo", null, true)),
                new ResponseSpec("$.ok == true", "$", null, null),
                idps.transport(),
                new MappingSpec(
                        new DirectionMappingSpec(
                                List.of(new MappingRule("rename", "$.a", "$.b", null, null, null)),
                                null),
                        new DirectionMappingSpec(
                                List.of(new MappingRule("rename", "$.raw", "$.mapped", null, null, null)),
                                null),
                        null),
                null);
        registry.save(mapped, Map.of(), ConnectorSpecStatus.PUBLISHED);
    }

}
