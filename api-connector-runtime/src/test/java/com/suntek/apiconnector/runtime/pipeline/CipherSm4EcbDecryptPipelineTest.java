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

class CipherSm4EcbDecryptPipelineTest {

    private static final String KEY = "0123456789abcdeffedcba9876543210";
    private static final String CIPHER_HEX = "8cd2182b020f1d459a719234394e2f06";
    private static final String PLAIN = "symkey-fixture";

    @Test
    void knownCiphertextDecryptsToFixtureSymKey() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/sm4-decrypt/key", KEY);
        client.loadPublished(definition("ENCRYPT"));

        ExecutionResult result = client.execute(new ExecuteCommand(
                "sm4-decrypt",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(header(transport.invocations().getFirst(), "X-Plain")).isEqualTo(PLAIN);
        assertThat(String.valueOf(result.trace())).doesNotContain(KEY);
    }

    @Test
    void encodingConfigFailsCompile() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: sm4-decrypt-encoding
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-decrypt/key
                    apiId: sm4-decrypt-encoding
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
                  cipherHex:
                    type: string
                    scope: GLOBAL
                    value: "%s"
                  plaintext:
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
                      X-Plain: "{flow.plaintext}"
                pipelines:
                  decrypt:
                    nodes:
                      - id: material
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.cipherHex }
                        ports:
                          out: { name: out, type: bytes }
                      - id: cipher
                        type: cipher.sm4-ecb-decrypt
                        config:
                          encoding: hex
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: secret, required: true }
                          out: { name: plaintext, type: string }
                    edges:
                      - from: material.out
                        to: cipher.in
                      - from:
                          secretRef: appSecret
                        to: cipher.key
                        sink: ENCRYPT
                flows:
                  business:
                    steps:
                      - id: decrypt
                        pipeline: decrypt
                        output:
                          flow.plaintext: cipher.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(CIPHER_HEX);
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void directSecretIntoCipherInFailsCompile() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: sm4-decrypt-direct
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-decrypt/key
                    apiId: sm4-decrypt-direct
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
                  decrypt:
                    nodes:
                      - id: cipher
                        type: cipher.sm4-ecb-decrypt
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: secret, required: true }
                          out: { name: plaintext, type: string }
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
    void hashSinkOnKeyIsDenied() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("HASH")))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void nonHexKeyFailsBeforeHttpWithoutKeyInMessage() {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/sm4-decrypt/key", "not-thirty-two-hex-characters!!");
        client.loadPublished(definition("ENCRYPT"));

        assertThatThrownBy(() -> client.execute(new ExecuteCommand(
                "sm4-decrypt",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(ExecuteException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause.getMessage()).doesNotContain("not-thirty-two");
                });
        assertThat(transport.invocations()).isEmpty();
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

    private static String definition(String sink) {
        return """
                schema:
                  version: 1
                definition:
                  id: sm4-decrypt
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-decrypt/key
                    apiId: sm4-decrypt
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
                  cipherHex:
                    type: string
                    scope: GLOBAL
                    value: "%s"
                  plaintext:
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
                      X-Plain: "{flow.plaintext}"
                pipelines:
                  decrypt:
                    nodes:
                      - id: material
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.cipherHex }
                        ports:
                          out: { name: out, type: bytes }
                      - id: cipher
                        type: cipher.sm4-ecb-decrypt
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: secret, required: true }
                          out: { name: plaintext, type: string }
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
                      - id: decrypt
                        pipeline: decrypt
                        output:
                          flow.plaintext: cipher.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(CIPHER_HEX, sink);
    }
}
