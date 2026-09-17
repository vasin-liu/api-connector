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

class MockDAuthFlowTest {

    @Test
    void sequentialHopsRunBeforeBusinessReplay() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                json(200, "{\"ticket\":\"tick-1\"}"),
                json(200, "{\"code\":\"code-9\"}"),
                json(200, "{\"token\":\"tok-d\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(resource("/definitions/mock-d.yaml"));
        ExecutionResult result = client.execute(command())
                .result()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(5);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest hopA = transport.invocations().get(1);
        RawHttpRequest hopB = transport.invocations().get(2);
        RawHttpRequest hopC = transport.invocations().get(3);
        RawHttpRequest replay = transport.invocations().get(4);
        assertThat(r1.uri().getPath()).isEqualTo("/v1/data");
        assertThat(header(r1, "Authorization")).isEmpty();
        assertThat(hopA.uri().getPath()).isEqualTo("/v1/auth/start");
        assertThat(hopB.uri().getPath()).isEqualTo("/v1/auth/challenge");
        assertThat(header(hopB, "X-Ticket")).isEqualTo("tick-1");
        assertThat(hopC.uri().getPath()).isEqualTo("/v1/auth/token");
        assertThat(header(hopC, "X-Code")).isEqualTo("code-9");
        assertThat(replay.uri().getPath()).isEqualTo("/v1/data");
        assertThat(header(replay, "Authorization")).isEqualTo("Bearer tok-d");
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
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
                "mock-d",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(20), false, Map.of())
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
        try (InputStream in = MockDAuthFlowTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
