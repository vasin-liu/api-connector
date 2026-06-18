package com.suntek.apiconnector.app;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.scripting.ScriptCompileService;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end invoke integration tests.
 *
 * @see docs/legacy-auth-inventory.md Wave 1 auth proof (ROADMAP SC#5, SC#6)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvokeIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ConnectorRegistry registry;

    @Autowired
    private ScriptCompileService scriptCompileService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.security.enabled", () -> "false");
        registry.add("integration.persistence.source", () -> "memory");
        registry.add("integration.persistence.sync-on-startup", () -> "false");
    }

    @BeforeEach
    void stubAndRebindDemoNone() {
        wireMock.stubFor(get(urlEqualTo("/get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\":\"http://demo/get\"}")));
        ConnectorSpec demo = registry.require("DEMO_NONE");
        ConnectorSpec rebound = new ConnectorSpec(
                demo.code3rd(),
                demo.version(),
                "http://localhost:" + wireMock.getPort(),
                demo.protocol(),
                demo.auth(),
                demo.endpoints(),
                demo.response(),
                demo.transport(),
                demo.mapping(),
                demo.transform());
        registry.save(rebound, null, ConnectorSpecStatus.PUBLISHED);
    }

    @Test
    void invokeDemoNoneEndpointExtractsData() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/DEMO_NONE/endpoints/echoGet/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("\"httpStatus\":200"));
        assertTrue(response.body().contains("http://demo/get"));
    }

    @Test
    void invokeUnknownAuthProfileReturnsStructuredError() throws Exception {
        ConnectorSpec demo = registry.require("DEMO_NONE");
        ConnectorSpec badAuth = new ConnectorSpec(
                demo.code3rd(),
                demo.version(),
                "http://localhost:" + wireMock.getPort(),
                demo.protocol(),
                Map.of("type", "unknown_auth_type"),
                demo.endpoints(),
                demo.response(),
                demo.transport(),
                demo.mapping(),
                demo.transform());
        registry.save(badAuth, null, ConnectorSpecStatus.PUBLISHED);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/DEMO_NONE/endpoints/echoGet/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"AUTH_PROFILE_MISSING\""));
        assertTrue(response.body().contains("profileType"));
        assertTrue(response.body().contains("unknown_auth_type"));
    }

    @Test
    void strictEndpointsRejectsFreePathOnIdps() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/IDPS/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"method\":\"GET\",\"path\":\"/evil\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("STRICT_ENDPOINTS"));
    }

    @Test
    void invokeBaiduWenxinAddsOAuthAccessTokenToOutboundQuery() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/oauth/2.0/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"wenxin-test-token\",\"expires_in\":3600}")));
        wireMock.stubFor(post(urlPathMatching("/rpc/2.0/ai_custom/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\":\"ok\"}")));

        ConnectorSpec wenxin = registry.require("BAIDU_WENXIN");
        registry.save(
                rebindBaseUrl(wenxin, wireMock.getPort()),
                Map.of("appId", "test-app", "appSecret", "test-secret"),
                ConnectorSpecStatus.PUBLISHED);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/BAIDU_WENXIN/endpoints/chatCompletionsPro/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"messages\":[]}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        wireMock.verify(postRequestedFor(urlPathMatching("/rpc/2.0/ai_custom/.*"))
                .withQueryParam("access_token", equalTo("wenxin-test-token")));
    }

    @Test
    void invokeDemoAkskSendsHmacSignatureHeaders() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\":\"http://demo/get\"}")));

        ConnectorSpec demo = registry.require("DEMO_AKSK");
        registry.save(
                rebindBaseUrl(demo, wireMock.getPort()),
                Map.of("publicKey", "ak-test", "appSecret", "sk-test"),
                ConnectorSpecStatus.PUBLISHED);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/DEMO_AKSK/endpoints/echoGet/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        wireMock.verify(getRequestedFor(urlPathEqualTo("/get"))
                .withHeader("X-Auth-Key", equalTo("ak-test"))
                .withHeader("X-Auth-Signature", matching("[0-9a-f]{64}"))
                .withHeader("X-Auth-Algorithm", equalTo("aksk_hmac_sha256")));
    }

    /**
     * ROADMAP SC#2: publish pre-warms script; second invoke uses compile cache (AUTH-03).
     */
    @Test
    void groovyAuthScriptCompileOnceAcrossTwoInvokes() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/demo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        String script = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer groovy-cache-token'], [:], null)
                """;
        ConnectorSpec groovyDemo = new ConnectorSpec(
                "GROOVY_INVOKE_TEST",
                "1.0.0",
                "http://localhost:" + wireMock.getPort(),
                "HTTP",
                Map.of("type", "groovy_auth_script", "script", script),
                List.of(new EndpointSpec("invoke", "GET", "/api/demo", null, true)),
                new ResponseSpec(null, "$.ok", null, null),
                null,
                null,
                null);

        int cacheSizeBefore = scriptCompileService.compiledScriptCacheSize();
        registry.save(groovyDemo, Map.of(), ConnectorSpecStatus.PUBLISHED);
        assertEquals(cacheSizeBefore + 1, scriptCompileService.compiledScriptCacheSize());

        String invokeUrl = baseUrl()
                + "/api/v1/integrations/GROOVY_INVOKE_TEST/endpoints/invoke/invoke";
        for (int i = 0; i < 2; i++) {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(invokeUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "invoke #" + (i + 1));
        }

        assertEquals(cacheSizeBefore + 1, scriptCompileService.compiledScriptCacheSize());
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/api/demo"))
                .withHeader("Authorization", equalTo("Bearer groovy-cache-token")));
    }

    /**
     * MAP-06 / SC#3: request mapping (a->b) runs BEFORE auth, so HMAC signs the mapped body
     * and the vendor receives {@code $.b}.
     */
    @Test
    void hmacSignsMappedRequestBody() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/post"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        ConnectorSpec mapAksk = new ConnectorSpec(
                "MAP_AKSK",
                "1.0.0",
                "http://localhost:" + wireMock.getPort(),
                "HTTP",
                Map.of("type", "aksk_hmac_sha256_v1"),
                List.of(new EndpointSpec("echoPost", "POST", "/post", null, true)),
                new ResponseSpec(null, "$", null, null),
                null,
                new MappingSpec(
                        new DirectionMappingSpec(
                                List.of(new MappingRule("rename", "$.a", "$.b", null, null, null)),
                                null),
                        null,
                        null),
                null);
        registry.save(
                mapAksk,
                Map.of("publicKey", "ak-test", "appSecret", "sk-test"),
                ConnectorSpecStatus.PUBLISHED);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/MAP_AKSK/endpoints/echoPost/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"{\\\"a\\\":1}\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        wireMock.verify(postRequestedFor(urlPathEqualTo("/post"))
                .withRequestBody(matchingJsonPath("$.b"))
                .withHeader("X-Auth-Signature", matching("[0-9a-f]{64}")));
    }

    /**
     * PIPE-01: unified invoke runs the full pipeline; response mapping renames vendor field
     * into the returned body.
     */
    @Test
    void unifiedInvokeRunsFullPipeline() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/full"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"raw\":\"hello\",\"ok\":true}")));

        ConnectorSpec full = new ConnectorSpec(
                "MAP_FULL",
                "1.0.0",
                "http://localhost:" + wireMock.getPort(),
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec("full", "GET", "/full", null, true)),
                new ResponseSpec("$.ok == true", "$", null, null),
                null,
                new MappingSpec(
                        null,
                        new DirectionMappingSpec(
                                List.of(new MappingRule("rename", "$.raw", "$.mapped", null, null, null)),
                                null),
                        null),
                null);
        registry.save(full, Map.of(), ConnectorSpecStatus.PUBLISHED);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl()
                                + "/api/v1/integrations/MAP_FULL/endpoints/full/invoke"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("mapped"));
        assertTrue(response.body().contains("hello"));
    }

    private static ConnectorSpec rebindBaseUrl(ConnectorSpec spec, int wireMockPort) {
        return new ConnectorSpec(
                spec.code3rd(),
                spec.version(),
                "http://localhost:" + wireMockPort,
                spec.protocol(),
                spec.auth(),
                spec.endpoints(),
                spec.response(),
                spec.transport(),
                spec.mapping(),
                spec.transform());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
