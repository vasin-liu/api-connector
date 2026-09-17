/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.time.ScriptedClock;
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

class MockCPipelineReplayTest {

    private static final String KEY = "secret/mock-c/api-key";

    @Test
    void m1_replayRebuildsTimestampAndHmacWithoutCloningR1() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                challenge(403, "nonce-1"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport, new ScriptedClock(1000L, 1001L, 1002L));
        client.loadPublished(mockC());
        ExecutionHandle handle = client.execute(command());
        String executionId = handle.executionId();
        ExecutionResult result = await(handle);

        assertThat(handle.executionId()).isEqualTo(executionId);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.httpStatus()).contains(200);
        assertThat(result.session()).isPresent();
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        assertThat(r1.method()).isEqualTo("POST");
        assertThat(r1.uri().getPath()).isEqualTo("/api/data");
        assertThat(header(r1, "X-Timestamp")).isEqualTo("1000");
        assertThat(header(r1, "Authorization")).isEmpty();
        assertThat(r2).isNotSameAs(r1);
        assertThat(header(r2, "X-Timestamp")).isEqualTo("1001");
        assertThat(header(r2, "Authorization")).isEqualTo("HMAC-SHA256 " + hmac("nonce-1", "1001"));
        assertThat(header(r2, "Authorization")).isNotEqualTo(header(r1, "Authorization"));
        assertThat(header(r2, "X-Timestamp")).isNotEqualTo(header(r1, "X-Timestamp"));
        assertThat(result.trace().decisions().stream().map(d -> d.type()).toList())
                .contains("CONDITION", "AUTH_TRIGGER", "CHALLENGE", "PIPELINE", "SESSION", "REPLAY");
        assertThat(String.valueOf(result.trace())).doesNotContain("HMAC-SHA256");
        assertThat(result.trace().decisions().stream()
                .flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && value.contains("HMAC-SHA256"));
    }

    @Test
    void c6_503RetriesSameTimestampWithoutAuthOrGenerationBump() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport, new ScriptedClock(1000L, 1001L));
        client.loadPublished(mockC());
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);
        assertThat(result.session()).isEmpty();
        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        assertThat(header(r1, "X-Timestamp")).isEqualTo("1000");
        assertThat(header(r2, "X-Timestamp")).isEqualTo("1000");
        assertThat(header(r1, "Authorization")).isEmpty();
        assertThat(header(r2, "Authorization")).isEmpty();
        assertThat(r2).isNotSameAs(r1);
    }

    @Test
    void m2_staleNonceRetriesFlowAndNeverSendsHmacOfNonce1OnR3() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                challenge(403, "nonce-1"),
                challenge(403, "nonce-2"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport, new ScriptedClock(1000L, 1001L, 1002L));
        client.loadPublished(mockC());
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(3);
        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        RawHttpRequest r3 = transport.invocations().get(2);
        String hmacNonce1 = "HMAC-SHA256 " + hmac("nonce-1", "1001");
        String hmacNonce2 = "HMAC-SHA256 " + hmac("nonce-2", "1002");
        assertThat(header(r1, "Authorization")).isEmpty();
        assertThat(header(r2, "Authorization")).isEqualTo(hmacNonce1);
        assertThat(header(r2, "X-Timestamp")).isEqualTo("1001");
        assertThat(header(r3, "X-Timestamp")).isEqualTo("1002");
        assertThat(header(r3, "Authorization")).isEqualTo(hmacNonce2);
        assertThat(header(r3, "Authorization")).isNotEqualTo(hmacNonce1);
        assertThat(result.session().orElseThrow().generation()).isEqualTo(2);
    }

    private static String hmac(String nonce, String timestamp) {
        return HmacSha256.hexUtf8(KEY, KEY + nonce + timestamp);
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "mock-c",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        );
    }

    private static String header(RawHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static RawHttpResponse challenge(int status, String nonce) {
        return new RawHttpResponse(
                status,
                Map.of("X-Challenge", List.of(nonce)),
                new ResponseBody.BytesBody("{}".getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
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

    private static String mockC() {
        try (InputStream in = MockCPipelineReplayTest.class.getResourceAsStream("/definitions/mock-c.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-c.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
