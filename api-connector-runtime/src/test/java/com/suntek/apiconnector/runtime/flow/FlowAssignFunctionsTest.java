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
import com.suntek.apiconnector.runtime.time.ScriptedClock;
import com.suntek.apiconnector.runtime.time.ScriptedNonce;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAssignFunctionsTest {

    @Test
    void isoOffsetUsesUtcClockAndEpochMillisStaysNumeric() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(ok());
        Phase0ApiClient client = new Phase0ApiClient(
                transport,
                new ScriptedClock(1000L, 2000L),
                new ScriptedNonce("1", "2")
        );
        client.loadPublished(yaml());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        RawHttpRequest request = transport.invocations().getFirst();
        String query = request.uri().getQuery();
        assertThat(query).contains("iso=1970-01-01T00:00:01Z");
        assertThat(query).contains("epoch=2000");
        assertThat(query).contains("n1=1");
        assertThat(query).contains("n2=2");
        assertThat(queryValue(request, "n1")).isNotEqualTo(queryValue(request, "n2"));
    }

    private static String queryValue(RawHttpRequest request, String name) {
        String q = request.uri().getQuery();
        if (q == null) {
            return "";
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && name.equals(part.substring(0, eq))) {
                return part.substring(eq + 1);
            }
        }
        return "";
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "assign-fn",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        );
    }

    private static RawHttpResponse ok() {
        return new RawHttpResponse(
                200,
                Map.of(),
                new ResponseBody.BytesBody("{}".getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String yaml() {
        return """
                schema:
                  version: 1
                definition:
                  id: assign-fn
                  revision: 1
                  authProfile: none
                credentials:
                  apiKey:
                    type: secret
                    valueRef: secret/assign-fn/api-key
                    apiId: assign-fn
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://assign.example"
                  iso:
                    type: string
                    scope: EXECUTION
                  epoch:
                    type: number
                    scope: EXECUTION
                  n1:
                    type: string
                    scope: EXECUTION
                  n2:
                    type: string
                    scope: EXECUTION
                limits:
                  maxAuthAttempts: 1
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  ping:
                    method: GET
                    url: "{global.baseUrl}/ping"
                    query:
                      iso: "{execution.iso}"
                      epoch: "{execution.epoch}"
                      n1: "{execution.n1}"
                      n2: "{execution.n2}"
                pipelines:
                  identity:
                    nodes:
                      - id: passthrough
                        type: passthrough
                    edges: []
                flows:
                  business:
                    steps:
                      - id: stamp
                        assign:
                          execution.iso: { now: isoOffset }
                          execution.epoch: { now: epochMillis }
                          execution.n1: { generate: nonce }
                          execution.n2: { generate: nonce }
                      - id: ping
                        request: ping
                        pipeline: identity
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
    }
}
