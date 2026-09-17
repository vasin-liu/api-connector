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

class ReplayPolicyUnknownOutcomeTest {

    @Test
    void mockI_writtenThenDroppedPostIsUnknownOutcomeOnce() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                new RawHttpResponse(0, Map.of(), new ResponseBody.EmptyBody(), false)
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(resource("/definitions/mock-i.yaml"));
        ExecutionResult result = await(client.execute(command("mock-i")));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.UNKNOWN_OUTCOME);
        assertThat(result.httpStatus()).isEmpty();
        assertThat(transport.invocations()).hasSize(1);
        assertThat(transport.invocations().getFirst().method()).isEqualTo("POST");
        assertThat(transport.invocations().getFirst().uri().getPath()).isEqualTo("/orders");
    }

    @Test
    void mockB_unsafeLoginDropDoesNotSendLoginAgain() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                new RawHttpResponse(0, Map.of(), new ResponseBody.EmptyBody(), false)
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(resource("/definitions/mock-b.yaml"));
        ExecutionResult result = await(client.execute(command("mock-b")));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.UNKNOWN_OUTCOME);
        assertThat(transport.invocations()).hasSize(2);
        assertThat(transport.invocations().get(0).uri().getPath()).isEqualTo("/v1/data");
        assertThat(transport.invocations().get(1).method()).isEqualTo("POST");
        assertThat(transport.invocations().get(1).uri().getPath()).isEqualTo("/v1/login");
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command(String apiId) {
        return new ExecuteCommand(
                apiId,
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
        try (InputStream in = ReplayPolicyUnknownOutcomeTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
