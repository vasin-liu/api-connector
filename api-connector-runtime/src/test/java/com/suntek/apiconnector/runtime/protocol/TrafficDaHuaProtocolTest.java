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
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
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

import static org.assertj.core.api.Assertions.assertThat;

class TrafficDaHuaProtocolTest {

    private static final String USER = "test-user";
    private static final String PASS = "test-pass";
    private static final String TOKEN = "test-token";
    private static final String SIGNATURE = "c11f8ffa3cd99605a9f23331c512b8b2";
    private static final String CHALLENGE_JSON =
            "{\"realm\":\"test-realm\",\"randomKey\":\"test-random-key\",\"encryptType\":\"MD5\"}";
    private static final String TOKEN_JSON = "{\"token\":\"" + TOKEN + "\",\"duration\":86400}";

    @Test
    void twoCompilesProduceTheSamePlanId() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.trafficDahua());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.trafficDahua());
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId()).matches("[0-9a-f]{64}");
        assertThat(first.definitionId()).isEqualTo("traffic-dahua");
        CompiledRequest business = first.requests().get("deviceStub");
        assertThat(business.headers()).anyMatch(binding ->
                "X-Subject-Token".equalsIgnoreCase(binding.name())
                        && binding.sink().orElse(null) == SecretSink.HEADER);
        assertThat(first.pipelines()).containsKeys(
                "hashPwd", "hashUserP1", "hashP2", "hashRealm", "hashSign");
        assertThat(first.pipelines().values().stream()
                .flatMap(p -> p.nodes().stream())
                .map(n -> String.valueOf(((Map<?, ?>) n).get("type"))))
                .contains("hasher.md5")
                .doesNotContain("hasher.md5-vendor");
    }

    @Test
    void firstBusiness401ThenTwoHopAuthThenSubjectTokenReplay() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                json(401, CHALLENGE_JSON),
                json(200, TOKEN_JSON),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = seeded(transport);
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(4);

        RawHttpRequest business1 = transport.invocations().get(0);
        RawHttpRequest challenge = transport.invocations().get(1);
        RawHttpRequest tokenAuth = transport.invocations().get(2);
        RawHttpRequest replay = transport.invocations().get(3);

        assertThat(business1.method()).isEqualTo("GET");
        assertThat(business1.uri().getPath()).isEqualTo("/videoService/devices/stub");
        assertThat(header(business1, "X-Subject-Token")).isEmpty();

        assertThat(challenge.method()).isEqualTo("POST");
        assertThat(challenge.uri().getPath()).isEqualTo("/videoService/accounts/authorize");
        String challengeBody = body(challenge);
        assertThat(challengeBody).contains("\"userName\":\"" + USER + "\"");
        assertThat(challengeBody).contains("\"clientType\":\"web\"");
        assertThat(challengeBody).doesNotContain("signature");
        assertThat(header(challenge, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(header(challenge, "X-Api-Version")).isEqualTo("V1.0");

        assertThat(tokenAuth.uri().getPath()).isEqualTo("/videoService/accounts/authorize");
        String tokenBody = body(tokenAuth);
        assertThat(tokenBody).contains("\"signature\":\"" + SIGNATURE + "\"");
        assertThat(tokenBody).contains("\"randomKey\":\"test-random-key\"");
        assertThat(tokenBody).contains("\"encryptType\":\"MD5\"");
        assertThat(tokenBody).contains("\"expiredTime\":86400");
        assertThat(tokenBody).contains("\"clientType\":\"web\"");
        assertThat(tokenBody).doesNotContain("\"method\"");

        assertThat(replay.uri().getPath()).isEqualTo("/videoService/devices/stub");
        assertThat(header(replay, "X-Subject-Token")).isEqualTo(TOKEN);
        assertThat(header(replay, "Authorization")).isEmpty();
        assertNoSecretInTrace(result);
    }

    @Test
    void validSessionSkipsAuthorize() throws Exception {
        AtomicInteger authorize = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/accounts/authorize")) {
                authorize.incrementAndGet();
                String body = body(request);
                if (body.contains("\"signature\"")) {
                    return json(200, TOKEN_JSON);
                }
                return json(401, CHALLENGE_JSON);
            }
            if (TOKEN.equals(header(request, "X-Subject-Token"))) {
                return json(200, "{\"ok\":true}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(authorize.get()).isEqualTo(2);
        int afterFirst = transport.invocations().size();

        ExecutionResult second = await(client.execute(command()));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(authorize.get()).isEqualTo(2);
        assertThat(transport.invocations()).hasSize(afterFirst + 1);
        assertThat(header(transport.invocations().getLast(), "X-Subject-Token")).isEqualTo(TOKEN);
        assertNoSecretInTrace(second);
    }

    @Test
    void challengeWithoutRandomKeyDoesNotStormDuringCooldown() throws Exception {
        AtomicInteger authorize = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/accounts/authorize")) {
                authorize.incrementAndGet();
                return json(401, "{\"realm\":\"test-realm\",\"encryptType\":\"MD5\"}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(authorize.get()).isEqualTo(1);

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(authorize.get()).isEqualTo(1);
        assertThat(header(transport.invocations().getLast(), "X-Subject-Token")).isEmpty();
        assertNoSecretInTrace(cooldown);
    }

    private static Phase0ApiClient seeded(FakeTransport transport) {
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/traffic-dahua/username", USER);
        client.secrets().put("secret/traffic-dahua/password", PASS);
        client.loadPublished(ProtocolDefinitions.trafficDahua());
        return client;
    }

    private static void assertNoSecretInTrace(ExecutionResult result) {
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(PASS, TOKEN, USER);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null
                        && (value.contains(PASS) || value.contains(TOKEN) || value.contains(USER)));
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

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "traffic-dahua",
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
