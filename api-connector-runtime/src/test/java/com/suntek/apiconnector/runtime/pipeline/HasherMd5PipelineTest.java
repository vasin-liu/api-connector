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

class HasherMd5PipelineTest {

    @Test
    void hashSinkConcatThenHasherProducesLowercaseDigestWithoutSecretsInTrace() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/hash-pwd/pass", "test-pass");
        client.secrets().put("secret/hash-pwd/user", "test-user");
        client.loadPublished(definition());

        ExecutionResult result = client.execute(new ExecuteCommand(
                "hash-pwd",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain("test-pass", "test-user");
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && (value.contains("test-pass") || value.contains("test-user")));
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String definition() {
        return """
                schema:
                  version: 1
                definition:
                  id: hash-pwd
                  revision: 1
                  authProfile: none
                credentials:
                  dahuaPass:
                    type: secret
                    valueRef: secret/hash-pwd/pass
                    apiId: hash-pwd
                  dahuaUser:
                    type: secret
                    valueRef: secret/hash-pwd/user
                    apiId: hash-pwd
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://hasher.example"
                  p1:
                    type: string
                    scope: FLOW
                  p2:
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
                pipelines:
                  hashPwd:
                    nodes:
                      - id: canonical
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - secretRef: dahuaPass
                              sink: HASH
                        ports:
                          in_secret: { type: secret, required: true }
                          out: { name: canonical, type: bytes }
                      - id: hasher
                        type: hasher.md5
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from: canonical.out
                        to: hasher.in
                  hashUserP1:
                    nodes:
                      - id: canonical
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - secretRef: dahuaUser
                              sink: HASH
                            - var: flow.p1
                        ports:
                          in_secret: { type: secret, required: true }
                          in_digest: { type: string, required: true }
                          out: { name: canonical, type: bytes }
                      - id: hasher
                        type: hasher.md5
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from: canonical.out
                        to: hasher.in
                flows:
                  business:
                    steps:
                      - id: hashPwd
                        pipeline: hashPwd
                        output:
                          flow.p1: hasher.hex
                      - id: hashUserP1
                        pipeline: hashUserP1
                        output:
                          flow.p2: hasher.hex
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
    }
}
