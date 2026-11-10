/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
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

class WireBodySecretBindingTest {

    @Test
    void bodySecretBindsAndRereadsTheSameBytes() throws Exception {
        String expected = "{\"appkey\":\"test-ak\"}";
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/wire-body-bind/appkey", "test-ak");
        client.loadPublished(yaml());

        ExecutionResult result = client.execute(new ExecuteCommand(
                "wire-body-bind",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        byte[] sent = transport.invocations().getFirst().body().orElseThrow();
        assertThat(sent).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
        assertThat(String.valueOf(result.trace())).doesNotContain("test-ak");
    }

    private static String yaml() {
        return """
                schema:
                  version: 1
                definition:
                  id: wire-body-bind
                  revision: 1
                  authProfile: none
                credentials:
                  appkey:
                    type: secret
                    valueRef: secret/wire-body-bind/appkey
                    apiId: wire-body-bind
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://bind.example"
                  wireBody:
                    type: string
                    scope: EXECUTION
                limits:
                  maxAuthAttempts: 1
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  ping:
                    method: POST
                    url: "{global.baseUrl}/ping"
                    body:
                      pipeline: sendBody
                pipelines:
                  wireBody:
                    nodes:
                      - id: literal
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - value: '{"appkey":"'
                            - secretRef: appkey
                              sink: BODY
                            - value: '"}'
                        ports:
                          out: { name: canonical, type: bytes }
                    edges: []
                  sendBody:
                    nodes:
                      - id: copy
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: execution.wireBody }
                        ports:
                          out: { name: canonical, type: bytes }
                    edges: []
                flows:
                  business:
                    steps:
                      - id: assemble
                        pipeline: wireBody
                        output:
                          execution.wireBody: literal.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
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
