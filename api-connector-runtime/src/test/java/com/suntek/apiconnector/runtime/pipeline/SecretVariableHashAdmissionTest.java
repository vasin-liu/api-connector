/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretVariableHashAdmissionTest {

    private static final String SECRET = "symkey-fixture";
    private static final String DIGEST = Sm3Hasher.hex(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void hashOnSecretVariableIsAllowed() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/var-hash/material", SECRET);
        client.loadPublished(definition("""
                            - var: flow.material
                              sink: HASH
                """));

        ExecutionResult result = client.execute(new ExecuteCommand(
                "var-hash",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(header(transport.invocations().getFirst(), "X-Digest")).isEqualTo(DIGEST);
        assertThat(String.valueOf(result.trace())).doesNotContain(SECRET);
    }

    @Test
    void omittedSinkOnSecretVariableIsDenied() {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/var-hash/material", SECRET);
        client.loadPublished(definition("""
                            - var: flow.material
                """));

        assertThatThrownBy(() -> client.execute(new ExecuteCommand(
                "var-hash",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ExecuteException.class);
        assertThat(transport.invocations()).isEmpty();
    }

    private static String definition(String parts) {
        return """
                schema:
                  version: 1
                definition:
                  id: var-hash
                  revision: 1
                  authProfile: none
                credentials:
                  material:
                    type: secret
                    valueRef: secret/var-hash/material
                    apiId: var-hash
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://var-hash.example"
                  material:
                    type: secret
                    scope: FLOW
                  digest:
                    type: string
                    scope: FLOW
                limits:
                  maxAuthAttempts: 1
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  ping:
                    method: GET
                    url: "{global.baseUrl}/ping"
                    headers:
                      X-Digest: "{flow.digest}"
                pipelines:
                  bind:
                    nodes:
                      - id: passthrough
                        type: passthrough
                    edges: []
                  hash:
                    nodes:
                      - id: material
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                PLACEHOLDER
                        ports:
                          out: { name: out, type: bytes }
                      - id: hasher
                        type: hasher.sm3
                        config:
                          encoding: hex
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from: material.out
                        to: hasher.in
                flows:
                  business:
                    steps:
                      - id: bindSecret
                        assign:
                          flow.material: { credential: material }
                      - id: hash
                        pipeline: hash
                        output:
                          flow.digest: hasher.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.replace("PLACEHOLDER", parts.stripTrailing());
    }

    private static String header(
            com.suntek.apiconnector.transport.RawHttpRequest request,
            String name
    ) {
        for (var entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
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
