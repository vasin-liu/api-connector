package com.suntek.integration.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void enableSecurity(DynamicPropertyRegistry registry) {
        registry.add("integration.security.enabled", () -> "true");
        registry.add("integration.security.runtime-api-keys[0]", () -> "test-runtime-key");
        registry.add("integration.security.admin-api-keys[0]", () -> "test-admin-key");
    }

    @Test
    void runtimeInvokeRequiresApiKey() throws Exception {
        HttpResponse<String> denied = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/IDPS/endpoints"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, denied.statusCode());

        HttpResponse<String> allowed = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + "/api/v1/integrations/IDPS/endpoints"))
                        .header("X-Integration-Api-Key", "test-runtime-key")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, allowed.statusCode());
    }

    @Test
    void actuatorHealthRemainsPublic() throws Exception {
        HttpResponse<String> health = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl() + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
