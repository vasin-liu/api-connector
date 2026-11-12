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

class TrafficShuyunProtocolTest {

    private static final String APP_ID = "test-app-id";
    private static final String APP_SECRET = "test-app-secret";
    private static final String PATH = "/sznszhddpt/rest/event/list";
    private static final String BODY = "{\"eventType\":\"200170001\"}";
    private static final String VECTOR_R1 =
            "bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb";
    private static final String VECTOR_R2 =
            "c6261c5b5b0eb59b92993bcb8954aec24e5436e1c50b8246d80765e9733aa882";
    private static final String VECTOR_R1_UPPER =
            "BCE0B91E9BA1E45AAE2CCD3341C7F5BCF0889E2B08D12ED7B3CD6DC9744C05CB";

    @Test
    void compileUsesConcatAndSm3WithoutVendorNode() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.shuyun());
        assertThat(plan.definitionId()).isEqualTo("shuyun");
        assertThat(plan.planId()).isNotBlank();
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("signHeaders", "wireBody");
        assertThat(types(plan, "signHeaders")).containsExactly("canonicalizer.concat", "hasher.sm3");
        assertThat(types(plan, "wireBody")).containsExactly("canonicalizer.concat");
        for (String pipelineId : plan.pipelines().keySet()) {
            assertThat(types(plan, pipelineId))
                    .doesNotContain("codec.json", "hasher.shuyun-sm3");
        }
        assertThat(nodeEncoding(plan, "signHeaders", "hasher.sm3")).isEqualTo("hex");

        ExecutionPlan again = PlanCompiler.compile(ProtocolDefinitions.shuyun());
        assertThat(again.planId()).isEqualTo(plan.planId());
        assertThat(plan.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.keytop()).planId());
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.shuyun().replace("hasher.sm3", "hasher.shuyun-sm3");
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
                new ScriptedClock(1000L, 1001L, 1002L),
                new ScriptedNonce("n1", "n2", "n3")
        );
        client.secrets().put("secret/shuyun/app-secret", APP_SECRET);
        client.loadPublished(ProtocolDefinitions.shuyun());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        byte[] body1 = r1.body().orElseThrow();
        byte[] body2 = r2.body().orElseThrow();

        assertThat(r1.method()).isEqualTo("POST");
        assertThat(r1.uri().getPath()).isEqualTo(PATH);
        assertThat(header(r1, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(header(r1, "x-app-id")).isEqualTo(APP_ID);
        assertThat(header(r1, "timestamp")).isEqualTo("1000");
        assertThat(header(r1, "nonce")).isEqualTo("n1");
        assertThat(header(r1, "signature")).isEqualTo(VECTOR_R1);
        assertThat(header(r1, "signature")).isNotEqualTo(VECTOR_R1_UPPER);
        assertThat(header(r1, "operationUserId")).isEqualTo("1");
        assertThat(header(r1, "operationUserName")).isEqualTo("idps");
        assertThat(header(r1, "operationUserCode")).isEqualTo("idps");
        assertThat(header(r1, "operationAppCode")).isEqualTo("jtdn");
        assertThat(new String(body1, StandardCharsets.UTF_8)).isEqualTo(BODY);
        assertThat(wire(r1)).doesNotContain(APP_SECRET);

        assertThat(r2).isNotSameAs(r1);
        assertThat(header(r2, "timestamp")).isEqualTo("1001");
        assertThat(header(r2, "nonce")).isEqualTo("n2");
        assertThat(header(r2, "signature")).isEqualTo(VECTOR_R2);
        assertThat(header(r2, "timestamp")).isNotEqualTo(header(r1, "timestamp"));
        assertThat(header(r2, "nonce")).isNotEqualTo(header(r1, "nonce"));
        assertThat(header(r2, "signature")).isNotEqualTo(header(r1, "signature"));
        assertThat(body2).isEqualTo(body1);
        assertThat(wire(r2)).doesNotContain(APP_SECRET);
        assertThat(String.valueOf(result.trace())).doesNotContain(APP_SECRET);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static String nodeEncoding(ExecutionPlan plan, String pipelineId, String type) {
        for (Map<String, Object> node : plan.pipelines().get(pipelineId).nodes()) {
            Map<String, Object> raw = YamlMaps.map(node);
            if (type.equals(String.valueOf(raw.get("type")))) {
                return String.valueOf(YamlMaps.map(raw.get("config")).get("encoding"));
            }
        }
        return "";
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

    private static String wire(RawHttpRequest request) {
        StringBuilder out = new StringBuilder();
        out.append(request.uri());
        request.headers().forEach((name, values) -> {
            out.append(' ').append(name).append('=');
            if (values != null) {
                out.append(String.join(",", values));
            }
        });
        request.body().ifPresent(bytes -> out.append(' ').append(new String(bytes, StandardCharsets.UTF_8)));
        return out.toString();
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "shuyun",
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
