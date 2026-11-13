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
import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CipherSm4EcbPipelineTest {

    private static final String PLAIN =
            "{\"alarmNo\":\"a1\",\"alarmTime\":\"2026-01-01 00:00:00\",\"deviceCode\":\"dev-1\","
                    + "\"factory\":\"test\",\"picPath\":\"https://cetc.example/p\",\"type\":\"98\","
                    + "\"videoPath\":\"https://cetc.example/v\"}";
    private static final String PUBLISHED_BASE64 =
            "ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g=";

    @Test
    void encryptSinkProducesPublishedBase64WithoutSecretsInTrace() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/sm4-demo/app-secret", "test-cetc-secret");
        client.loadPublished(definition("ENCRYPT", "base64"));

        ExecutionResult result = client.execute(new ExecuteCommand(
                "sm4-demo",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(header(transport.invocations().getFirst(), "X-Cipher")).isEqualTo(PUBLISHED_BASE64);
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain("test-cetc-secret");
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && value.contains("test-cetc-secret"));
    }

    @Test
    void directSecretIntoCipherInFailsCompile() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: sm4-direct
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-direct/app-secret
                    apiId: sm4-direct
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
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
                  encrypt:
                    nodes:
                      - id: cipher
                        type: cipher.sm4-ecb
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: secret, required: true }
                          out: { name: ciphertext, type: string }
                    edges:
                      - from:
                          secretRef: appSecret
                        to: cipher.in
                        sink: ENCRYPT
                      - from:
                          secretRef: appSecret
                        to: cipher.key
                        sink: ENCRYPT
                flows:
                  business:
                    steps:
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void hexEncodingFailsCompile() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("ENCRYPT", "hex")))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void hmacSinkOnKeyEdgeIsDenied() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("HMAC", "base64")))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void encryptSinkOnWireBodyIsDenied() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: sm4-body
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-body/app-secret
                    apiId: sm4-body
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
                limits:
                  maxAuthAttempts: 1
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  post:
                    method: POST
                    url: "{global.baseUrl}/post"
                    body:
                      pipeline: wireBody
                pipelines:
                  wireBody:
                    nodes:
                      - id: literal
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - secretRef: appSecret
                              sink: ENCRYPT
                        ports:
                          out: { name: canonical, type: bytes }
                    edges: []
                flows:
                  business:
                    steps:
                      - id: post
                        request: post
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    private static String header(
            com.suntek.apiconnector.transport.RawHttpRequest request,
            String name
    ) {
        var values = request.headers().get(name);
        if (values == null) {
            for (var entry : request.headers().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String definition(String sink, String encoding) {
        return """
                schema:
                  version: 1
                definition:
                  id: sm4-demo
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-demo/app-secret
                    apiId: sm4-demo
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
                  plainLiteral:
                    type: string
                    scope: GLOBAL
                    value: '%s'
                  ciphertext:
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
                      X-Cipher: "{flow.ciphertext}"
                pipelines:
                  encrypt:
                    nodes:
                      - id: material
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.plainLiteral }
                        ports:
                          out: { name: canonical, type: bytes }
                      - id: cipher
                        type: cipher.sm4-ecb
                        config:
                          encoding: %s
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: secret, required: true }
                          out: { name: ciphertext, type: string }
                    edges:
                      - from: material.out
                        to: cipher.in
                      - from:
                          secretRef: appSecret
                        to: cipher.key
                        sink: %s
                flows:
                  business:
                    steps:
                      - id: encrypt
                        pipeline: encrypt
                        output:
                          flow.ciphertext: cipher.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(PLAIN.replace("'", "''"), encoding, sink);
    }
}
