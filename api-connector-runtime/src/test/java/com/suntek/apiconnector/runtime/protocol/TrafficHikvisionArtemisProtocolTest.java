/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.protocol;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.pipeline.HmacSha256;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.time.ScriptedClock;
import com.suntek.apiconnector.runtime.time.ScriptedNonce;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficHikvisionArtemisProtocolTest {

    private static final String AK = "test-ak";
    private static final String SK = "test-sk";
    private static final String QUERY = "size=20&start=0";
    private static final String VECTOR_R1 = "8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=";
    private static final String VECTOR_R2 = "lR1hXqQhapV53HJazMDZPUn4NXMywjKFaMKeyLZeYxU=";
    private static final String VECTOR_R1_HEX = "f2f3396769f8dc74a90d1a4745b2893505601db8163fd13ed687192338e1134a";

    @Test
    void compileUsesTwoPipelinesAndBase64Hmac() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.hikvisionArtemis());
        assertThat(plan.definitionId()).isEqualTo("hikvision-artemis");
        assertThat(plan.planId()).isNotBlank();
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("resourceQuery", "stringToSignHmac");
        assertThat(types(plan, "resourceQuery")).contains("canonicalizer.sorted-query");
        List<String> hmacTypes = types(plan, "stringToSignHmac");
        assertThat(hmacTypes).contains("canonicalizer.concat", "signer.hmac-sha256");
        assertThat(hmacTypes).doesNotContain("canonicalizer.artemis-string-to-sign", "signer.artemis-hmac");
        assertThat(hmacEncoding(plan)).isEqualTo("base64");
        ExecutionPlan again = PlanCompiler.compile(ProtocolDefinitions.hikvisionArtemis());
        assertThat(again.planId()).isEqualTo(plan.planId());
    }

    @Test
    void unknownArtemisNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.hikvisionArtemis()
                .replace("canonicalizer.concat", "canonicalizer.artemis-string-to-sign");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void retryFlowRebuildsTimestampNonceAndSignature() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(
                transport,
                new ScriptedClock(1000L, 2000L, 3000L),
                new ScriptedNonce("1", "2", "3")
        );
        client.secrets().put("secret/hikvision-artemis/access-key", AK);
        client.secrets().put("secret/hikvision-artemis/secret-key", SK);
        client.loadPublished(ProtocolDefinitions.hikvisionArtemis());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        assertThat(r1.method()).isEqualTo("GET");
        assertThat(r1.uri().getPath()).isEqualTo("/artemis/api/demo");
        assertThat(query(r1)).contains("start=0");
        assertThat(query(r1)).contains("size=20");
        assertThat(header(r1, "X-Ca-Key")).isEqualTo(AK);
        assertThat(header(r1, "Accept")).isEqualTo("*/*");
        assertThat(header(r1, "Content-Type")).isEqualTo("application/text;charset=UTF-8");
        assertThat(header(r1, "X-Ca-Signature-Headers")).isEqualTo("X-Ca-Key,X-Ca-Nonce,X-Ca-Timestamp");
        assertThat(header(r1, "X-Ca-Timestamp")).isEqualTo("1000");
        assertThat(header(r1, "X-Ca-Nonce")).isEqualTo("1");
        assertThat(header(r1, "X-Ca-Signature")).isEqualTo(VECTOR_R1);
        assertThat(header(r1, "X-Ca-Signature")).isNotEqualTo(VECTOR_R1_HEX);
        assertThat(header(r1, "X-Ca-Signature")).isNotEqualTo(HmacSha256.base64Utf8(SK, QUERY));
        assertThat(query(r1) + " " + r1.headers()).doesNotContain(SK);

        assertThat(r2).isNotSameAs(r1);
        assertThat(header(r2, "X-Ca-Timestamp")).isEqualTo("2000");
        assertThat(header(r2, "X-Ca-Nonce")).isEqualTo("2");
        assertThat(header(r2, "X-Ca-Signature")).isEqualTo(VECTOR_R2);
        assertThat(header(r2, "X-Ca-Timestamp")).isNotEqualTo(header(r1, "X-Ca-Timestamp"));
        assertThat(header(r2, "X-Ca-Nonce")).isNotEqualTo(header(r1, "X-Ca-Nonce"));
        assertThat(header(r2, "X-Ca-Signature")).isNotEqualTo(header(r1, "X-Ca-Signature"));
        assertThat(String.valueOf(result.trace())).doesNotContain(SK);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static String hmacEncoding(ExecutionPlan plan) {
        for (Map<String, Object> node : plan.pipelines().get("stringToSignHmac").nodes()) {
            Map<String, Object> raw = YamlMaps.map(node);
            if ("signer.hmac-sha256".equals(String.valueOf(raw.get("type")))) {
                return String.valueOf(YamlMaps.map(raw.get("config")).get("encoding"));
            }
        }
        return "";
    }

    private static String query(RawHttpRequest request) {
        String q = request.uri().getRawQuery() == null ? request.uri().getQuery() : request.uri().getRawQuery();
        return q == null ? "" : q;
    }

    private static String header(RawHttpRequest request, String name) {
        List<String> values = request.headers().get(name);
        if (values == null) {
            for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    values = entry.getValue();
                    break;
                }
            }
        }
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "hikvision-artemis",
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
