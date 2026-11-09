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

class TrafficHikvisionPmsProtocolTest {

    private static final String SK = "test-sk";
    private static final String URI_PATH = "/getParkingInfo";
    private static final String BODY = "{\"appkey\":\"test-ak\"}";
    private static final byte[] BODY_BYTES = BODY.getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = "EDAA4B3136EA75DF9A435C95B920DD9E";
    private static final String TOKEN_LOWER = "edaa4b3136ea75df9a435c95b920dd9e";
    private static final String TOKEN_BASE64 = "7apLMTbqdd+aQ1yVuSDdng==";

    @Test
    void compileUsesConcatBodyAndHexUpperMd5() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.hikvisionPms());
        assertThat(plan.definitionId()).isEqualTo("hikvision-pms");
        assertThat(plan.planId()).isNotBlank();
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("wireBody", "tokenHash");
        assertThat(types(plan, "wireBody")).containsExactly("canonicalizer.concat");
        assertThat(types(plan, "tokenHash")).contains("canonicalizer.concat", "hasher.md5");
        for (String pipelineId : plan.pipelines().keySet()) {
            assertThat(types(plan, pipelineId))
                    .doesNotContain(
                            "codec.json",
                            "canonicalizer.pms-token",
                            "signer.hmac-sha256",
                            "signer.pms-token"
                    );
        }
        assertThat(nodeEncoding(plan, "tokenHash", "hasher.md5")).isEqualTo("hex-upper");

        ExecutionPlan again = PlanCompiler.compile(ProtocolDefinitions.hikvisionPms());
        assertThat(again.planId()).isEqualTo(plan.planId());
        assertThat(plan.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.hikvisionArtemis()).planId());
        assertThat(plan.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.hikvisionArtemisPost()).planId());
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.hikvisionPms()
                .replace("canonicalizer.concat", "canonicalizer.pms-token");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void retryRequestKeepsTokenAndBodyDeterministic() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"parkName\":\"stub\"}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/hikvision-pms/secret-key", SK);
        client.loadPublished(ProtocolDefinitions.hikvisionPms());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);

        assertThat(r1.method()).isEqualTo("POST");
        assertThat(r1.uri().getPath()).isEqualTo(URI_PATH);
        assertThat(query(r1)).contains("token=" + TOKEN);
        assertThat(header(r1, "Content-Type")).isEqualTo("application/json;charset=UTF-8");

        byte[] wireBody = r1.body().orElseThrow();
        assertThat(wireBody).isEqualTo(BODY_BYTES);

        String material = URI_PATH + new String(wireBody, StandardCharsets.UTF_8) + SK;
        assertThat(header(r1, "Content-Type")).isNotEmpty();
        assertThat(Md5Hasher.encode(material.getBytes(StandardCharsets.UTF_8), DigestEncoding.HEX_UPPER))
                .isEqualTo(TOKEN);
        assertThat(TOKEN).isNotEqualTo(TOKEN_LOWER);
        assertThat(TOKEN).isNotEqualTo(TOKEN_BASE64);
        assertThat(Md5Hasher.hex(material.getBytes(StandardCharsets.UTF_8))).isEqualTo(TOKEN_LOWER);
        assertThat(query(r1)).doesNotContain(TOKEN_LOWER);

        assertThat(r2).isNotSameAs(r1);
        assertThat(r2.body().orElseThrow()).isEqualTo(BODY_BYTES);
        assertThat(query(r2)).contains("token=" + TOKEN);
        assertThat(query(r1) + " " + r1.headers() + " " + BODY).doesNotContain(SK);
        assertThat(query(r2) + " " + r2.headers() + " " + BODY).doesNotContain(SK);
        assertThat(String.valueOf(result.trace())).doesNotContain(SK);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static String nodeEncoding(ExecutionPlan plan, String pipelineId, String nodeType) {
        for (Map<String, Object> node : plan.pipelines().get(pipelineId).nodes()) {
            Map<String, Object> raw = YamlMaps.map(node);
            if (nodeType.equals(String.valueOf(raw.get("type")))) {
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
                "hikvision-pms",
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
