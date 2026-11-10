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
import com.suntek.apiconnector.runtime.pipeline.DigestEncoding;
import com.suntek.apiconnector.runtime.pipeline.Md5Hasher;
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

class TrafficKeytopProtocolTest {

    private static final String SECRET = "EED96C219E83450A";
    private static final String APP_ID = "85d15350778b11e9bbaa506b4b2f6421";
    private static final String PATH = "/api/wec/GetFreeSpaceNum";
    private static final String SIGNED =
            "amount=100&freeDetail=[{\"code\":\"\",\"money\":100,\"time\":0,\"type\":0}]"
                    + "&freeMoney=100&freeTime=0&orderNo=闽C12345&outOrderNo=T20200306124536001"
                    + "&parkId=1000001&payMethod=6&paySource=85d15350778b11e9bbaa506b4b2f6421"
                    + "&payTime=2020-03-06 10:57:22&payType=1006&payableAmount=200"
                    + "&paymentExt={\"deviceNo\":\"123456\"}"
                    + "&reqId=5be4e3e6d5704a7d91ccbd9731d970f5&ts=1583744086841&"
                    + SECRET;
    private static final String KEY = "B19F7863ADCC8B5442A757AC7B90F6AC";
    private static final String KEY_LOWER = "b19f7863adcc8b5442a757ac7b90f6ac";
    private static final String BODY =
            "{\"amount\":100,\"appId\":\"" + APP_ID + "\",\"freeDetail\":\"[{\\\"code\\\":\\\"\\\",\\\"money\\\":100,"
                    + "\\\"time\\\":0,\\\"type\\\":0}]\",\"freeMoney\":100,\"freeTime\":0,\"orderNo\":\"闽C12345\","
                    + "\"outOrderNo\":\"T20200306124536001\",\"parkId\":\"1000001\",\"payMethod\":6,"
                    + "\"paySource\":\"" + APP_ID + "\",\"payTime\":\"2020-03-06 10:57:22\",\"payType\":1006,"
                    + "\"payableAmount\":200,\"paymentExt\":\"{\\\"deviceNo\\\":\\\"123456\\\"}\","
                    + "\"reqId\":\"5be4e3e6d5704a7d91ccbd9731d970f5\",\"ts\":1583744086841,\"key\":\""
                    + KEY + "\"}";

    @Test
    void compileUsesSortedQueryConcatAndHexUpperMd5() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.keytop());
        assertThat(plan.definitionId()).isEqualTo("keytop");
        assertThat(plan.planId()).isNotBlank();
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("signMaterial", "digestKey", "wireBody");
        assertThat(types(plan, "signMaterial")).containsExactly("canonicalizer.sorted-query");
        assertThat(types(plan, "digestKey")).containsExactly("canonicalizer.concat", "hasher.md5");
        assertThat(types(plan, "wireBody")).containsExactly("canonicalizer.concat");
        for (String pipelineId : plan.pipelines().keySet()) {
            assertThat(types(plan, pipelineId))
                    .doesNotContain("codec.json", "canonicalizer.keytop-sign");
        }
        assertThat(nodeConfig(plan, "signMaterial", "canonicalizer.sorted-query").get("encoding"))
                .isEqualTo("none");
        assertThat(nodeConfig(plan, "digestKey", "hasher.md5").get("encoding")).isEqualTo("hex-upper");

        ExecutionPlan again = PlanCompiler.compile(ProtocolDefinitions.keytop());
        assertThat(again.planId()).isEqualTo(plan.planId());
        assertThat(plan.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.hikvisionPms()).planId());
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.keytop()
                .replace("canonicalizer.sorted-query", "canonicalizer.keytop-sign");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void retryRequestKeepsKeyAndBody() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"freeSpaceNum\":1}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/keytop/app-secret", SECRET);
        client.loadPublished(ProtocolDefinitions.keytop());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        String body = new String(r1.body().orElseThrow(), StandardCharsets.UTF_8);

        assertThat(r1.method()).isEqualTo("POST");
        assertThat(r1.uri().getPath()).isEqualTo(PATH);
        assertThat(r1.uri().getRawQuery()).isNull();
        assertThat(header(r1, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(header(r1, "version")).isEqualTo("1.0.0");
        assertThat(header(r1, "accept-language")).isEqualTo("zh-CN");
        assertThat(body).isEqualTo(BODY);
        assertThat(body).contains("\"appId\":\"" + APP_ID + "\"");
        assertThat(body).contains("\"key\":\"" + KEY + "\"");
        assertThat(body).doesNotContain(KEY_LOWER);
        assertThat(Md5Hasher.encode(SIGNED.getBytes(StandardCharsets.UTF_8), DigestEncoding.HEX_UPPER))
                .isEqualTo(KEY);
        assertThat(Md5Hasher.hex(SIGNED.getBytes(StandardCharsets.UTF_8))).isEqualTo(KEY_LOWER);

        assertThat(r2).isNotSameAs(r1);
        assertThat(r2.body().orElseThrow()).isEqualTo(r1.body().orElseThrow());
        assertThat(wireText(r1)).doesNotContain(SECRET);
        assertThat(wireText(r2)).doesNotContain(SECRET);
        assertThat(String.valueOf(result.trace())).doesNotContain(SECRET);
    }

    private static String wireText(RawHttpRequest request) {
        String query = request.uri().getRawQuery() == null ? "" : request.uri().getRawQuery();
        String body = new String(request.body().orElse(new byte[0]), StandardCharsets.UTF_8);
        return query + " " + request.headers() + " " + body;
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static Map<String, Object> nodeConfig(ExecutionPlan plan, String pipelineId, String nodeType) {
        for (Map<String, Object> node : plan.pipelines().get(pipelineId).nodes()) {
            Map<String, Object> raw = YamlMaps.map(node);
            if (nodeType.equals(String.valueOf(raw.get("type")))) {
                return YamlMaps.map(raw.get("config"));
            }
        }
        return Map.of();
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
                "keytop",
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
