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
import com.suntek.apiconnector.runtime.plan.NamedBinding;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.time.ScriptedClock;
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

class GaodeTrafficProtocolTest {

    private static final String AK = "test-ak";
    private static final String SK = "test-sk";
    private static final String VECTOR_TS_1000 = "7de16153d564bf1afd3b97d75f6c771a4bd7e7ac88d8d1648608b10241056155";

    @Test
    void compileUsesSortedQueryNotConcat() {
        ExecutionPlan plan = PlanCompiler.compile(ProtocolDefinitions.gaodeTraffic());
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        List<String> types = plan.pipelines().get("signQuery").nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
        assertThat(types).contains("canonicalizer.sorted-query", "signer.hmac-sha256");
        assertThat(types).doesNotContain("canonicalizer.concat");
        assertThat(plan.requests().get("rectangleTraffic").query().stream().map(NamedBinding::name).toList())
                .containsExactly("city", "key", "timestamp", "sig");
    }

    @Test
    void unknownNodeTypeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.gaodeTraffic()
                .replace("canonicalizer.sorted-query", "canonicalizer.mystery");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void retryFlowRebuildsTimestampAndHmacWithoutCloningR1() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(503, "{\"error\":\"UNAVAILABLE\"}"),
                json(200, "{\"status\":\"1\"}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport, new ScriptedClock(1000L, 1001L, 1002L));
        client.secrets().put("secret/gaode-traffic/app-key", AK);
        client.secrets().put("secret/gaode-traffic/app-secret", SK);
        client.loadPublished(ProtocolDefinitions.gaodeTraffic());

        ExecutionResult result = await(client.execute(command()));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(2);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        assertThat(r1.method()).isEqualTo("GET");
        assertThat(r1.uri().getPath()).isEqualTo("/v3/traffic/status/rectangle");
        assertThat(query(r1)).contains("city=110000");
        assertThat(query(r1)).contains("key=" + AK);
        assertThat(query(r1)).contains("timestamp=1000");
        assertThat(queryValue(r1, "sig")).isEqualTo(VECTOR_TS_1000);
        assertThat(r2).isNotSameAs(r1);
        assertThat(queryValue(r2, "timestamp")).isEqualTo("1001");
        assertThat(queryValue(r2, "sig")).isEqualTo(hmac("1001"));
        assertThat(queryValue(r2, "timestamp")).isNotEqualTo(queryValue(r1, "timestamp"));
        assertThat(queryValue(r2, "sig")).isNotEqualTo(queryValue(r1, "sig"));
        assertThat(String.valueOf(result.trace())).doesNotContain(SK, VECTOR_TS_1000, hmac("1001"));
    }

    private static String hmac(String timestamp) {
        return HmacSha256.hexUtf8(SK, "city=110000&key=test-ak&timestamp=" + timestamp);
    }

    private static String query(RawHttpRequest request) {
        String q = request.uri().getQuery();
        return q == null ? "" : q;
    }

    private static String queryValue(RawHttpRequest request, String name) {
        String q = query(request);
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && name.equals(part.substring(0, eq))) {
                return part.substring(eq + 1);
            }
        }
        return "";
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "gaode-traffic",
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
