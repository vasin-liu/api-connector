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

class TrafficCetcProtocolTest {

    private static final String APP_SECRET = "test-cetc-secret";
    private static final String PATH = "/detect/third/traffic/event/collect";
    private static final String PLAIN =
            "{\"alarmNo\":\"a1\",\"alarmTime\":\"2026-01-01 00:00:00\",\"deviceCode\":\"dev-1\","
                    + "\"factory\":\"test\",\"picPath\":\"https://cetc.example/p\",\"type\":\"98\","
                    + "\"videoPath\":\"https://cetc.example/v\"}";
    private static final String PUBLISHED_BODY =
            "{\"data\":\"ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g=\"}";
    private static final String RESPONSE = "{\"code\":200,\"msg\":\"ok\"}";

    @Test
    void compileUsesConcatAndSm4WithoutVendorNode() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.cetc());
        assertThat(plan.definitionId()).isEqualTo("cetc");
        assertThat(plan.planId()).isNotBlank();
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("encryptBody", "wireBody");
        assertThat(types(plan, "encryptBody")).containsExactly("canonicalizer.concat", "cipher.sm4-ecb");
        assertThat(types(plan, "wireBody")).containsExactly("canonicalizer.concat");
        for (String pipelineId : plan.pipelines().keySet()) {
            assertThat(types(plan, pipelineId)).doesNotContain("cipher.cetc-sm4");
        }
        assertThat(nodeEncoding(plan, "encryptBody", "cipher.sm4-ecb")).isEqualTo("base64");

        ExecutionPlan again = PlanCompiler.compile(ProtocolDefinitions.cetc());
        assertThat(again.planId()).isEqualTo(plan.planId());
        assertThat(plan.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.shuyun()).planId());
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.cetc().replace("cipher.sm4-ecb", "cipher.cetc-sm4");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void collectBodyMatchesPublishedSm4AndResponseStaysPlaintext() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(json(200, RESPONSE));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/cetc/app-secret", APP_SECRET);
        client.loadPublished(ProtocolDefinitions.cetc());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(1);

        RawHttpRequest request = transport.invocations().getFirst();
        String body = new String(request.body().orElseThrow(), StandardCharsets.UTF_8);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().getPath()).isEqualTo(PATH);
        assertThat(header(request, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(body).isEqualTo(PUBLISHED_BODY);
        assertThat(body).isNotEqualTo(PLAIN);
        assertThat(wire(request)).doesNotContain(APP_SECRET);
        assertThat(String.valueOf(result.trace())).doesNotContain(APP_SECRET);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && value.contains(APP_SECRET));

        assertThat(result.body()).isInstanceOf(ResponseBody.BytesBody.class);
        ResponseBody.BytesBody responseBody = (ResponseBody.BytesBody) result.body();
        assertThat(new String(responseBody.bytes(), StandardCharsets.UTF_8)).isEqualTo(RESPONSE);
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
                "cetc",
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
