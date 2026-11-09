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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RetryFlowRequiresAuthTest {

    @Test
    void retryFlowWithoutAuthenticationFlowFailsAfterOneSend() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(503, "{\"error\":\"UNAVAILABLE\"}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(definition("retry-flow-missing-auth", "RETRY_FLOW"));

        ExecutionResult result = await(client.execute(command("retry-flow-missing-auth")));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(transport.invocations()).hasSize(1);
        assertThat(transport.invocations().getFirst().method()).isEqualTo("POST");
        assertThat(transport.invocations().getFirst().uri().getPath()).isEqualTo("/demo");
    }

    @Test
    void retryRequestWithoutAuthenticationFlowSendsAgain() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(definition("retry-request-no-auth", "RETRY_REQUEST"));

        ExecutionResult result = await(client.execute(command("retry-request-no-auth")));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);
        assertThat(transport.invocations().get(0).uri().getPath()).isEqualTo("/demo");
        assertThat(transport.invocations().get(1).uri().getPath()).isEqualTo("/demo");
        assertThat(transport.invocations().get(1)).isNotSameAs(transport.invocations().get(0));
    }

    private static String definition(String id, String retryAction) {
        return """
                schema:
                  version: 1
                definition:
                  id: %s
                  revision: 1
                  authProfile: retry-route-v1
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://retry.example"
                limits:
                  maxAuthAttempts: 2
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 15s
                requests:
                  demo:
                    method: POST
                    url: "{global.baseUrl}/demo"
                    replay:
                      replayability: SAFE
                      allowAutomaticReplay: true
                      maxAttempts: 2
                pipelines:
                  identity:
                    nodes:
                      - id: passthrough
                        type: passthrough
                    edges: []
                flows:
                  business:
                    steps:
                      - id: demo
                        request: demo
                        pipeline: identity
                        transitions:
                          - when:
                              status: 503
                            action: %s
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(id, retryAction);
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
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
}
