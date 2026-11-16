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

class CipherRsaPkcs1PipelineTest {

    @Test
    void encryptDecryptsBackToS2() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(definition("base64"));

        ExecutionResult result = client.execute(new ExecuteCommand(
                "rsa-demo",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        String ciphertext = header(transport.invocations().getFirst(), "X-Cipher");
        assertThat(RsaPkcs1CipherTest.decrypt(ciphertext)).isEqualTo(RsaPkcs1CipherTest.S2);
    }

    @Test
    void directSecretIntoCipherInFailsCompile() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: rsa-direct
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/rsa-direct/app-secret
                    apiId: rsa-direct
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://rsa.example"
                  publicKey:
                    type: string
                    scope: GLOBAL
                    value: "%s"
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
                      - id: keyMaterial
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.publicKey }
                        ports:
                          out: { name: canonical, type: bytes }
                      - id: cipher
                        type: cipher.rsa-pkcs1
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: bytes, required: true }
                          out: { name: ciphertext, type: string }
                    edges:
                      - from:
                          secretRef: appSecret
                        to: cipher.in
                        sink: HASH
                      - from: keyMaterial.out
                        to: cipher.key
                flows:
                  business:
                    steps:
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(RsaPkcs1CipherTest.SPKI);
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void hexEncodingFailsCompile() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("hex")))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void hmacSinkOnSm4KeyEdgeIsDenied() {
        assertThatThrownBy(() -> PlanCompiler.compile("""
                schema:
                  version: 1
                definition:
                  id: sm4-hmac
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm4-hmac/app-secret
                    apiId: sm4-hmac
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm4.example"
                  plainLiteral:
                    type: string
                    scope: GLOBAL
                    value: "plain"
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
                          encoding: base64
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
                        sink: HMAC
                flows:
                  business:
                    steps:
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """))
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

    private static String definition(String encoding) {
        return """
                schema:
                  version: 1
                definition:
                  id: rsa-demo
                  revision: 1
                  authProfile: none
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://rsa.example"
                  plainLiteral:
                    type: string
                    scope: GLOBAL
                    value: "%s"
                  publicKey:
                    type: string
                    scope: GLOBAL
                    value: "%s"
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
                      - id: keyMaterial
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.publicKey }
                        ports:
                          out: { name: canonical, type: bytes }
                      - id: cipher
                        type: cipher.rsa-pkcs1
                        config:
                          encoding: %s
                        ports:
                          in: { name: in, type: bytes, required: true }
                          key: { name: key, type: bytes, required: true }
                          out: { name: ciphertext, type: string }
                    edges:
                      - from: material.out
                        to: cipher.in
                      - from: keyMaterial.out
                        to: cipher.key
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
                """.formatted(RsaPkcs1CipherTest.S2, RsaPkcs1CipherTest.SPKI, encoding);
    }
}
