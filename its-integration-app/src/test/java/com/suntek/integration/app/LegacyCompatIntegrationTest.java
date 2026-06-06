package com.suntek.integration.app;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.engine.ConnectorSpecStatus;
import com.suntek.integration.spec.model.ConnectorSpec;
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

}
