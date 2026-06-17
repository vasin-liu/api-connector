package com.suntek.apiconnector.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行时端点目录 API 与 Catalog 端点数量一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectorEndpointsApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.security.enabled", () -> "false");
        registry.add("integration.persistence.source", () -> "memory");
        registry.add("integration.persistence.sync-on-startup", () -> "false");
    }

    @ParameterizedTest
    @CsvSource({
            "IDPS,19",
            "GAODE_OPEN_PLATFORM,4",
            "GAODE_TRAFFIC,9",
            "BAIDU_MAP,4",
            "BAIDU_WENXIN,2",
            "BaiduGpt,2"
    })
    void listEndpointsMatchesCatalogSize(String code3rd, int expectedCount) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/" + code3rd + "/endpoints"))
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode endpoints = objectMapper.readTree(response.body());
        assertTrue(endpoints.isArray());
        assertEquals(expectedCount, endpoints.size(), "endpoint count for " + code3rd);

        Set<String> ids = new HashSet<>();
        for (JsonNode endpoint : endpoints) {
            assertTrue(endpoint.hasNonNull("id"));
            assertTrue(endpoint.hasNonNull("method"));
            assertTrue(endpoint.hasNonNull("path"));
            assertTrue(endpoint.hasNonNull("invokeUrl"));
            assertTrue(endpoint.get("enabled").asBoolean());
            assertTrue(ids.add(endpoint.get("id").asText()), "duplicate endpoint id");
            assertTrue(endpoint.get("invokeUrl").asText().contains("/endpoints/" + endpoint.get("id").asText() + "/invoke"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "IDPS,roadSpeeds,roadclid",
            "GAODE_OPEN_PLATFORM,trafficStatusRectangle,rectangle"
    })
    void listEndpointsIncludesCatalogParameters(String code3rd, String endpointId, String paramName)
            throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/" + code3rd + "/endpoints"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode endpoints = objectMapper.readTree(response.body());
        JsonNode target = null;
        for (JsonNode endpoint : endpoints) {
            if (endpointId.equals(endpoint.get("id").asText())) {
                target = endpoint;
                break;
            }
        }
        assertTrue(target != null, "endpoint not listed: " + endpointId);
        assertTrue(target.has("parameters"), "parameters missing on " + endpointId);
        boolean found = false;
        for (JsonNode param : target.get("parameters")) {
            if (paramName.equals(param.get("name").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "parameter " + paramName + " missing on " + endpointId);
    }

    @ParameterizedTest
    @CsvSource({"DEMO_NONE", "DEMO_AKSK"})
    void demoConnectorsExposeEndpoints(String code3rd) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/" + code3rd + "/endpoints"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode endpoints = objectMapper.readTree(response.body());
        assertEquals(1, endpoints.size());
        assertEquals("echoGet", endpoints.get(0).get("id").asText());
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN_VENDOR", "NOT_EXISTS"})
    void unknownConnectorReturns404(String code3rd) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/" + code3rd + "/endpoints"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertFalse(response.body().isBlank());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
