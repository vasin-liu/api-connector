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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficQyhGpsProtocolTest {

    private static final String USER = "test-user";
    private static final String KEY = "0123456789abcdeffedcba9876543210";
    private static final String TOKEN = "test-token";
    private static final String SYM = "symkey-fixture";
    private static final String CIPHER = "8cd2182b020f1d459a719234394e2f06";
    private static final String LOGIN_BODY =
            "{\"sign\":\"1a625df0ea71ff8731a498b38638987d0ad71e0bcfe1cb79e987de599a29c86f\","
                    + "\"t\":1700000001000,\"sysId\":\"test-sys\"}";
    private static final String SIGN_1 = "8da4c1ef6d160c49f7e5494c8caa76b02c677f0d795e77a914a74b81029a3647";
    private static final String SIGN_2 = "9b4464d0f66c0b9de9d640bcfe9a5fc8126772f4533985198b85b88ea6f1675c";
    private static final String TOKEN_OK =
            "{\"success\":true,\"result\":{\"token\":\"" + TOKEN + "\",\"str\":\"" + CIPHER
                    + "\",\"timeOut\":3600}}";
    private static final String TOKEN_FAIL = "{\"success\":false,\"result\":{}}";
    private static final String TOKEN_NO_STR =
            "{\"success\":true,\"result\":{\"token\":\"" + TOKEN + "\"}}";

    @Test
    void twoCompilesProduceTheSamePlanIdDistinctFromCetc() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.qyhGps());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.qyhGps());
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.definitionId()).isEqualTo("qyh-gps");
        assertThat(first.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.cetc()).planId());
        assertThat(first.pipelines()).containsKeys("loginSign", "decryptSym", "querySign");
        assertThat(types(first, "decryptSym")).contains("cipher.sm4-ecb-decrypt");
        assertThat(types(first, "querySign")).contains("canonicalizer.percent-encode", "hasher.sm3");
        assertThat(ProtocolDefinitions.qyhGps()).contains("cipher.sm4-ecb-decrypt");
        assertThat(ProtocolDefinitions.qyhGps()).contains("canonicalizer.percent-encode");
        assertThat(ProtocolDefinitions.qyhGps()).doesNotContain("cipher.qyh-sm4");
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.qyhGps().replace("cipher.sm4-ecb-decrypt", "cipher.qyh-sm4");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void probeLoginReplayThenSecondExecuteSkipsGetToken() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                json(200, TOKEN_OK),
                json(200, "{\"ok\":true}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = seeded(transport, 1700000000000L, 1700000001000L, 1700000002000L);

        ExecutionResult first = await(client.execute(command()));
        assertThat(first.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(3);

        RawHttpRequest probe = transport.invocations().get(0);
        RawHttpRequest login = transport.invocations().get(1);
        RawHttpRequest replay = transport.invocations().get(2);

        assertThat(probe.uri().getPath()).endsWith("/queryGps");
        assertThat(header(probe, "X-Access-Token")).isEmpty();
        assertThat(header(probe, "X-Sign")).isEmpty();
        assertThat(query(probe)).contains("app_id=test-gps-app", "api_key=test-gps-key");

        assertThat(login.uri().getPath()).endsWith("/getToken");
        assertThat(query(login)).contains("app_id=test-token-app", "api_key=test-token-key");
        assertThat(header(login, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(body(login)).isEqualTo(LOGIN_BODY);

        assertThat(body(replay)).isEqualTo("{\"plateNo\":\"A12345\"}");
        assertThat(header(replay, "X-Access-Token")).isEqualTo(TOKEN);
        assertThat(header(replay, "X-Username")).isEqualTo(USER);
        assertThat(header(replay, "X-System")).isEqualTo("test-sys");
        assertThat(header(replay, "X-Time")).isEqualTo("1700000000000");
        assertThat(header(replay, "X-Sign")).isEqualTo(SIGN_1);
        assertThat(first.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(first.session().orElseThrow().status()).isEqualTo("VALID");
        assertNoSecretLeak(first, probe, login, replay);

        ExecutionResult second = await(client.execute(command()));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(4);
        RawHttpRequest later = transport.invocations().get(3);
        assertThat(later.uri().getPath()).endsWith("/queryGps");
        assertThat(header(later, "X-Time")).isEqualTo("1700000002000");
        assertThat(header(later, "X-Sign")).isEqualTo(SIGN_2);
        assertThat(header(later, "X-Access-Token")).isEqualTo(TOKEN);
        assertNoSecretLeak(second, later);
    }

    @Test
    void successFalseDoesNotSignQueryDuringCooldown() throws Exception {
        AtomicInteger getToken = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/getToken")) {
                getToken.incrementAndGet();
                return json(200, TOKEN_FAIL);
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport, 1700000000000L, 1700000001000L, 1700000002000L);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(getToken.get()).isEqualTo(1);
        int afterFirst = transport.invocations().size();

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(getToken.get()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(afterFirst);
        assertNoSecretLeak(cooldown);
    }

    @Test
    void missingStrDoesNotStormGetTokenDuringCooldown() throws Exception {
        AtomicInteger getToken = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/getToken")) {
                getToken.incrementAndGet();
                return json(200, TOKEN_NO_STR);
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport, 1700000000000L, 1700000001000L, 1700000002000L);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(getToken.get()).isEqualTo(1);

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(getToken.get()).isEqualTo(1);
        assertThat(header(transport.invocations().getLast(), "X-Access-Token")).isEmpty();
        assertNoSecretLeak(cooldown);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static Phase0ApiClient seeded(FakeTransport transport, long... ticks) {
        Phase0ApiClient client = new Phase0ApiClient(transport, new ScriptedClock(ticks));
        client.secrets().put("secret/qyh-gps/username", USER);
        client.secrets().put("secret/qyh-gps/agreed-key", KEY);
        client.loadPublished(ProtocolDefinitions.qyhGps());
        return client;
    }

    private static void assertNoSecretLeak(ExecutionResult result, RawHttpRequest... requests) {
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(KEY, SYM, USER, TOKEN);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null
                        && (value.contains(KEY) || value.contains(SYM) || value.contains(USER) || value.contains(TOKEN)));
        for (RawHttpRequest request : requests) {
            assertThat(wire(request)).doesNotContain(KEY, SYM);
        }
    }

    private static String header(RawHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static String body(RawHttpRequest request) {
        return new String(request.body().orElse(new byte[0]), StandardCharsets.UTF_8);
    }

    private static String query(RawHttpRequest request) {
        return request.uri().getRawQuery() == null ? "" : request.uri().getRawQuery();
    }

    private static String wire(RawHttpRequest request) {
        return request.uri() + " " + request.headers() + " " + body(request);
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "qyh-gps",
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
