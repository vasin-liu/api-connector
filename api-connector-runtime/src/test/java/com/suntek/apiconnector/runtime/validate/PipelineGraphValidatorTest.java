/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.validate;

import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineGraphValidatorTest {

    @Test
    void p0_mockCCompiles() {
        var plan = PlanCompiler.compile(mockC());
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("jsonBody", "challengeHmac");
    }

    @Test
    void p4_cycleFailsCompile() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: canonical.out\n        to: hmac.in\n      - from: hmac.out\n        to: canonical.in_secret"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_CYCLE);
    }

    @Test
    void p2_stringToBytesWithoutCodec() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: hmac.hex\n        to: hmac.in"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p3_missingRequiredEdge() {
        String yaml = mockC().replace(
                "    edges:\n      - from: canonical.out\n        to: hmac.in\n      - from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC\n",
                "    edges:\n      - from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC\n"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_PORT_REQUIRED);
    }

    @Test
    void p5_hmacKeyFromStringNonce() {
        String yaml = mockC().replace(
                "- from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC",
                "- from:\n          var: flow.nonce\n        to: hmac.key\n        sink: HMAC"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p1_crossPipelineEdgeIllegal() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: json.out\n        to: hmac.in"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p6_hmacStringOutputCannotBeHttpBody() {
        String yaml = mockC().replace(
                "    body:\n      pipeline: jsonBody",
                "    body:\n      pipeline: challengeHmac"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void unknownNodeTypeIsRejected() {
        String yaml = mockC().replace("canonicalizer.concat", "canonicalizer.sorted-query-typo");
        assertCode(yaml, ValidationCodes.VAL_PIPE_UNKNOWN_NODE);
    }

    @Test
    void unknownSortedQueryEncodingIsRejected() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: encoding-reject
                  revision: 1
                  authProfile: none
                credentials:
                  apiKey:
                    type: secret
                    valueRef: secret/encoding-reject/api-key
                    apiId: encoding-reject
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://encoding.example"
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
                  signQuery:
                    nodes:
                      - id: canonical
                        type: canonicalizer.sorted-query
                        config:
                          encoding: mystery
                          separator: "&"
                          params:
                            city:
                              value: "110000"
                        ports:
                          in: { name: query, type: object, required: true }
                          out: { name: canonical, type: bytes }
                    edges: []
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
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p7_codecThenHmacWithoutEdgesFailsType() {
        String yaml = mockC().replace(
                "          out: { name: body, type: bytes }\n    edges: []",
                "          out: { name: body, type: bytes }\n      - id: danglingHmac\n        type: signer.hmac-sha256\n        ports:\n          in: { name: canonical, type: bytes, required: true }\n          key: { name: key, type: secret, required: true }\n          out: { name: hex, type: string }\n    edges: []"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void unknownHasherTypeIsRejected() {
        String yaml = mockC().replace("signer.hmac-sha256", "hasher.md5-vendor");
        assertCode(yaml, ValidationCodes.VAL_PIPE_UNKNOWN_NODE);
    }

    @Test
    void secretEdgeDirectlyIntoHasherInFailsType() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: hasher-secret-edge
                  revision: 1
                  authProfile: none
                credentials:
                  dahuaPass:
                    type: secret
                    valueRef: secret/hasher-secret-edge/pass
                    apiId: hasher-secret-edge
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://hasher.example"
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
                  hashPass:
                    nodes:
                      - id: hasher
                        type: hasher.md5
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from:
                          secretRef: dahuaPass
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
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void stringEdgeDirectlyIntoHasherInFailsType() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: hasher-string-edge
                  revision: 1
                  authProfile: none
                credentials: {}
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://hasher.example"
                  digest:
                    type: string
                    scope: FLOW
                    value: "abc"
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
                  hashString:
                    nodes:
                      - id: hasher
                        type: hasher.md5
                        ports:
                          in: { name: in, type: bytes, required: true }
                          out: { name: hex, type: string }
                    edges:
                      - from:
                          var: flow.digest
                        to: hasher.in
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
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void hmacSinkOnHashAdmissionConcatIsDenied() {
        String yaml = hashPwdYaml("HMAC");
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void omittedSinkOnHashAdmissionConcatIsDenied() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: hash-omit-sink
                  revision: 1
                  authProfile: none
                credentials:
                  dahuaPass:
                    type: secret
                    valueRef: secret/hash-omit-sink/pass
                    apiId: hash-omit-sink
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://hasher.example"
                  p1:
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
                flows:
                  business:
                    steps:
                      - id: hash
                        pipeline: hashPwd
                        output:
                          flow.p1: hasher.hex
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """;
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void hashSinkOnConcatIntoHasherCompiles() {
        var plan = PlanCompiler.compile(hashPwdYaml("HASH"));
        assertThat(plan.pipelines()).containsKey("hashPwd");
    }

    private static String hashPwdYaml(String sink) {
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
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://hasher.example"
                  p1:
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
                              sink: %s
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
                flows:
                  business:
                    steps:
                      - id: hash
                        pipeline: hashPwd
                        output:
                          flow.p1: hasher.hex
                      - id: ping
                        request: ping
                        transitions:
                          - when:
                              status: 200
                            action: SUCCESS
                """.formatted(sink);
    }

    private static void assertCode(String yaml, String code) {
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(code));
    }

    private static String mockC() {
        try (InputStream in = PipelineGraphValidatorTest.class.getResourceAsStream("/definitions/mock-c.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-c.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
