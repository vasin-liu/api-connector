/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LinearExecuteMockATest {

    @Test
    void a1_success200OneCall() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        ExecutionResult result = execute(transport).result().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.httpStatus()).contains(200);
        assertThat(transport.invocations()).hasSize(1);
        assertThat(transport.invocations().getFirst().uri().toString())
                .isEqualTo("https://mock-a.example/v1/status");
    }

    @Test
    void a2_unmatched403IsFailureOneCall() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(403, "{\"error\":\"DENIED\"}"));
        ExecutionResult result = execute(transport).result().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(result.httpStatus()).contains(403);
        assertThat(transport.invocations()).hasSize(1);
    }

    @Test
    void a4_writtenThenDroppedIsUnknownOutcomeNotTimeout() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                new RawHttpResponse(0, Map.of(), new ResponseBody.EmptyBody(), false)
        );
        ExecutionResult result = execute(transport).result().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.UNKNOWN_OUTCOME);
        assertThat(result.outcome()).isNotEqualTo(StepOutcomeType.TIMEOUT);
        assertThat(result.httpStatus()).isEmpty();
        assertThat(transport.invocations()).hasSize(1);
    }

    private static ExecutionHandle execute(FakeTransport transport) {
        ExecutionPlan plan = PlanCompiler.compile(mockA());
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.registerPublished(plan);
        return client.execute(new ExecuteCommand(
                "mock-a",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        ));
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String mockA() {
        try (InputStream in = LinearExecuteMockATest.class.getResourceAsStream("/definitions/mock-a.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-a.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
