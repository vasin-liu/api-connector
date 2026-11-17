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

class TrafficQyhMetroProtocolTest {

    private static final String TOKEN = "test-metro-token";
    private static final String PASS_ID = "test-pass";
    private static final String HOST = "qyh-metro.example";
    private static final String PATH = "/jtj/in-out";
    private static final String QUERY = "start_time=2026-01-01T00:00:00&end_time=2026-01-01T00:10:00";
    private static final String VECTOR_R1 =
            "F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5";
    private static final String VECTOR_R2 =
            "0B3FF5CB28670D23AA4266EF97AF9512DBB7463BE874501E10800789CF45033E";
    private static final String BODY_OK = "{\"ok\":true}";

    @Test
    void compileUsesConcatAndSha256WithoutVendorNode() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.qyhMetro());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.qyhMetro());
        assertThat(first.definitionId()).isEqualTo("qyh-metro");
        assertThat(first.planId()).isNotBlank();
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.qyhGps()).planId());
        assertThat(first.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(types(first, "signHeaders")).containsExactly("canonicalizer.concat", "hasher.sha256");
        assertThat(nodeEncoding(first, "signHeaders", "hasher.sha256")).isEqualTo("hex-upper");
        assertThat(ProtocolDefinitions.qyhMetro()).contains("hasher.sha256");
        assertThat(ProtocolDefinitions.qyhMetro()).contains("epochSeconds");
        assertThat(types(first, "signHeaders")).doesNotContain("hasher.qyh-sha256");
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.qyhMetro().replace("hasher.sha256", "hasher.qyh-sha256");
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
                json(200, BODY_OK)
        );
        Phase0ApiClient client = new Phase0ApiClient(
                transport,
                new ScriptedClock(1_700_000_000_000L, 1_700_000_002_000L),
                new ScriptedNonce("1", "2")
        );
        client.secrets().put("secret/qyh-metro/token", TOKEN);
        client.loadPublished(ProtocolDefinitions.qyhMetro());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);

        assertThat(r1.method()).isEqualTo("GET");
        assertThat(r1.uri().getHost()).isEqualTo(HOST);
        assertThat(r1.uri().getPath()).isEqualTo(PATH);
        assertThat(r1.uri().getQuery()).isEqualTo(QUERY);
        assertThat(r1.body()).isEmpty();
        assertThat(header(r1, "x-tif-paasid")).isEqualTo(PASS_ID);
        assertThat(header(r1, "timestamp")).isEqualTo("1700000000");
        assertThat(header(r1, "nonce")).isEqualTo("1");
        assertThat(header(r1, "signature")).isEqualTo(VECTOR_R1);
        assertThat(wire(r1)).doesNotContain(TOKEN);

        assertThat(r2).isNotSameAs(r1);
        assertThat(r2.uri().getQuery()).isEqualTo(QUERY);
        assertThat(header(r2, "timestamp")).isEqualTo("1700000002");
        assertThat(header(r2, "nonce")).isEqualTo("2");
        assertThat(header(r2, "signature")).isEqualTo(VECTOR_R2);
        assertThat(header(r2, "timestamp")).isNotEqualTo(header(r1, "timestamp"));
        assertThat(header(r2, "nonce")).isNotEqualTo(header(r1, "nonce"));
        assertThat(header(r2, "signature")).isNotEqualTo(header(r1, "signature"));
        assertThat(wire(r2)).doesNotContain(TOKEN);

        assertThat(result.body()).isInstanceOf(ResponseBody.BytesBody.class);
        assertThat(new String(((ResponseBody.BytesBody) result.body()).bytes(), StandardCharsets.UTF_8))
                .isEqualTo(BODY_OK);

        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(TOKEN);
        assertThat(trace).doesNotContain(PASS_ID);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && (value.contains(TOKEN) || value.contains(PASS_ID)));
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
                "qyh-metro",
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
