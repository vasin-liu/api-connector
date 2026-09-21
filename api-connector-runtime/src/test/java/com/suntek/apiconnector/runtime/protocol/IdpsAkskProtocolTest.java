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

class IdpsAkskProtocolTest {

    private static final String AK = "test-ak";
    private static final String SK = "test-sk";
    private static final String QUERY_LINE = "city=110000&q=a%2Ab";
    private static final String VECTOR_R1 = "f4722b6df8d00aae9995b83ef056f3442c8c13c32571a82007ca54a762aa7de6";
    private static final String VECTOR_R2 = "5d5a0d988a600516ce38e5b078e07d04c0583187e8496065e3e92349bbcda6dc";
    private static final String HMAC_QUERY_ONLY = "0a09bb7975e2144b5cbd8a4b559262390d39c48ad1af1f2a68e641fda2ce5f2e";

    @Test
    void compileUsesTwoPipelinesAndLfSeparator() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.idpsAksk());
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("rfc3986Query", "envelopeHmac");
        List<String> queryTypes = types(plan, "rfc3986Query");
        assertThat(queryTypes).contains("canonicalizer.sorted-query");
        assertThat(queryTypes).doesNotContain("canonicalizer.aksk-envelope", "canonicalizer.aksk-string");
        List<String> hmacTypes = types(plan, "envelopeHmac");
        assertThat(hmacTypes).contains("canonicalizer.concat", "signer.hmac-sha256");
        assertThat(hmacTypes).doesNotContain("canonicalizer.aksk-envelope");
        String separator = envelopeSeparator(plan);
        assertThat(separator).isEqualTo("\n");
        assertThat(separator).hasSize(1);
        assertThat((int) separator.charAt(0)).isEqualTo(0x0A);
    }

    @Test
    void unknownAkskNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.idpsAksk()
                .replace("canonicalizer.concat", "canonicalizer.aksk-envelope");
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
        client.secrets().put("secret/idps-aksk/access-key", AK);
        client.secrets().put("secret/idps-aksk/secret-key", SK);
        client.loadPublished(ProtocolDefinitions.idpsAksk());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        assertThat(r1.method()).isEqualTo("GET");
        assertThat(r1.uri().getPath()).isEqualTo("/api/v2/demo");
        assertThat(query(r1)).contains("city=110000");
        assertThat(query(r1)).contains("q=a*b");
        assertThat(query(r1)).doesNotContain("%2A");
        assertThat(header(r1, "X-Auth-Key")).isEqualTo(AK);
        assertThat(header(r1, "X-Auth-Algorithm")).isEqualTo("aksk_hmac_sha256");
        assertThat(header(r1, "X-Auth-Timestamp")).isEqualTo("1970-01-01T00:00:01Z");
        assertThat(header(r1, "X-Auth-SnowflakeID")).isEqualTo("1");
        assertThat(header(r1, "X-Auth-Signature")).isEqualTo(VECTOR_R1);
        assertThat(VECTOR_R1).isNotEqualTo(HMAC_QUERY_ONLY);
        assertThat(VECTOR_R1).isNotEqualTo(HmacSha256.hexUtf8(SK, QUERY_LINE));

        assertThat(r2).isNotSameAs(r1);
        assertThat(header(r2, "X-Auth-Timestamp")).isEqualTo("1970-01-01T00:00:02Z");
        assertThat(header(r2, "X-Auth-SnowflakeID")).isEqualTo("2");
        assertThat(header(r2, "X-Auth-Signature")).isEqualTo(VECTOR_R2);
        assertThat(header(r2, "X-Auth-Timestamp")).isNotEqualTo(header(r1, "X-Auth-Timestamp"));
        assertThat(header(r2, "X-Auth-SnowflakeID")).isNotEqualTo(header(r1, "X-Auth-SnowflakeID"));
        assertThat(header(r2, "X-Auth-Signature")).isNotEqualTo(header(r1, "X-Auth-Signature"));
        assertThat(String.valueOf(result.trace())).doesNotContain(SK, VECTOR_R1, VECTOR_R2);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static String envelopeSeparator(ExecutionPlan plan) {
        for (Map<String, Object> node : plan.pipelines().get("envelopeHmac").nodes()) {
            Map<String, Object> raw = YamlMaps.map(node);
            if ("canonicalizer.concat".equals(String.valueOf(raw.get("type")))) {
                return String.valueOf(YamlMaps.map(raw.get("config")).get("separator"));
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
                "idps-aksk",
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
