/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MockECookieStoreTest {

    @Test
    void cookiesAreAttachedOnlyFromStore() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                new RawHttpResponse(
                        200,
                        Map.of("Set-Cookie", List.of("sid=abc; Domain=mock-e.example; Path=/; Secure")),
                        new ResponseBody.BytesBody("{\"token\":\"tok-e\"}".getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                        true
                ),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(resource("/definitions/mock-e.yaml"));
        ExecutionResult result = client.execute(command())
                .result()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(3);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r3 = transport.invocations().get(2);
        assertThat(cookie(r1)).isEmpty();
        assertThat(authorization(r1)).isEmpty();
        assertThat(authorization(r3)).isEqualTo("Bearer tok-e");
        assertThat(cookie(r3)).isEqualTo("sid=abc");
        String fromStore = client.sessions()
                .cookies(new com.suntek.apiconnector.core.session.SessionLookupKey("mock-e", "cookie-token", "account"))
                .cookiesFor(r3.uri());
        assertThat(cookie(r3)).isEqualTo(fromStore);
    }

    private static String cookie(RawHttpRequest request) {
        return header(request, "Cookie");
    }

    private static String authorization(RawHttpRequest request) {
        return header(request, "Authorization");
    }

    private static String header(RawHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "mock-e",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        );
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String resource(String path) {
        try (InputStream in = MockECookieStoreTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
