package com.suntek.apiconnector.app;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
