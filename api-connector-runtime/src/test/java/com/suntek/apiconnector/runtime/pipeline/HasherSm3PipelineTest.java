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

class HasherSm3PipelineTest {

    private static final String R1_HEX =
            "bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb";

    @Test
    void hashSinkConcatThenHasherProducesLowercaseDigestWithoutSecretsInTrace() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/sm3-demo/app-secret", "test-app-secret");
        client.loadPublished(definition("HASH", "hex"));

        ExecutionResult result = client.execute(new ExecuteCommand(
                "sm3-demo",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
        )).result().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(header(transport.invocations().getFirst(), "X-Digest")).isEqualTo(R1_HEX);
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain("test-app-secret");
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && value.contains("test-app-secret"));
    }

    @Test
    void directSecretIntoHasherInFailsCompile() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: sm3-direct
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm3-direct/app-secret
                    apiId: sm3-direct
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm3.example"
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
                  hash:
                    nodes:
                      - id: hasher
                        type: hasher.sm3
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from:
                          secretRef: appSecret
                        to: hasher.in
                        sink: HASH
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
    void unknownEncodingFailsCompile() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("HASH", "rot13")))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_TYPE));
    }

    @Test
    void bodySinkOnSm3FeederIsDenied() {
        assertThatThrownBy(() -> PlanCompiler.compile(definition("BODY", "hex")))
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
                  id: sm3-demo
                  revision: 1
                  authProfile: none
                credentials:
                  appSecret:
                    type: secret
                    valueRef: secret/sm3-demo/app-secret
                    apiId: sm3-demo
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://sm3.example"
                  appId:
                    type: string
                    scope: GLOBAL
                    value: "test-app-id"
                  stamp:
                    type: string
                    scope: GLOBAL
                    value: "1000"
                  nonce:
                    type: string
                    scope: GLOBAL
                    value: "n1"
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
                  sign:
                    nodes:
                      - id: canonical
                        type: canonicalizer.concat
                        config:
                          separator: ""
                          parts:
                            - { var: global.appId }
                            - secretRef: appSecret
                              sink: %s
                            - { var: global.stamp }
                            - { var: global.nonce }
                        ports:
                          out: { name: canonical, type: bytes }
                      - id: hasher
                        type: hasher.sm3
                        config:
                          encoding: %s
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from: canonical.out
                        to: hasher.in
                flows:
                  business:
                    steps:
                      - id: sign
                        pipeline: sign
                        output:
                          flow.digest: hasher.out
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(sink, encoding);
    }
}
