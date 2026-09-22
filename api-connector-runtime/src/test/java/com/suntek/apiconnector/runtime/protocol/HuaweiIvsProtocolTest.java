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
import com.suntek.apiconnector.runtime.session.CookieStore;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HuaweiIvsProtocolTest {

    private static final String USER = "test-user";
    private static final String PASS = "test-pass";
    private static final String JSID = "test-jsid";
    private static final String LOGIN_JSON = "{\"resultCode\":\"0\",\"data\":{\"sessionToken\":\"do-not-use-this\"}}";
    private static final String SET_COOKIE = "JSESSIONID=" + JSID + "; Path=/";

    @Test
    void twoCompilesProduceTheSamePlanId() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.huaweiIvs());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.huaweiIvs());
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId()).matches("[0-9a-f]{64}");
        assertThat(first.definitionId()).isEqualTo("huawei-ivs");
        CompiledRequest business = first.requests().get("deviceList");
        assertThat(business.cookies()).contains("fromStore");
        assertThat(business.headers()).noneMatch(binding -> "Authorization".equalsIgnoreCase(binding.name()));
        assertThat(first.variables()).doesNotContainKey("token");
        CompiledRequest login = first.requests().get("login");
        assertThat(login.cookies()).contains("acceptSetCookie");
    }

    @Test
    void firstBusiness401ThenLoginThenCookieReplayWithoutAuthorization() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                loginOk(),
                json(200, "{\"resultCode\":\"0\"}")
        );
        Phase0ApiClient client = seeded(transport);
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(3);

        RawHttpRequest business1 = transport.invocations().get(0);
        RawHttpRequest login = transport.invocations().get(1);
        RawHttpRequest replay = transport.invocations().get(2);
        assertThat(business1.method()).isEqualTo("GET");
        assertThat(business1.uri().getPath()).isEqualTo("/device/deviceList/v1.0");
        assertThat(header(business1, "Cookie")).isEmpty();
        assertThat(header(business1, "Authorization")).isEmpty();

        assertThat(login.method()).isEqualTo("POST");
        assertThat(login.uri().getPath()).isEqualTo("/loginInfo/login/v1.0");
        String loginBody = new String(login.body().orElse(new byte[0]), StandardCharsets.UTF_8);
        assertThat(loginBody).contains("\"userName\":\"" + USER + "\"");
        assertThat(loginBody).contains("\"password\":\"" + PASS + "\"");

        assertThat(replay).isNotSameAs(business1);
        assertThat(replay.uri().getPath()).isEqualTo("/device/deviceList/v1.0");
        assertThat(header(replay, "Cookie")).isEqualTo("JSESSIONID=" + JSID);
        assertThat(header(replay, "Authorization")).isEmpty();
        assertThat(header(replay, "Cookie")).doesNotContain("do-not-use-this");
        assertNoSecretInTrace(result);
    }

    @Test
    void validCookieSessionSkipsLogin() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/loginInfo/login/v1.0")) {
                logins.incrementAndGet();
                return loginOk();
            }
            if (header(request, "Cookie").contains("JSESSIONID=" + JSID)) {
                return json(200, "{\"resultCode\":\"0\"}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(logins.get()).isEqualTo(1);
        int afterFirst = transport.invocations().size();

        ExecutionResult second = await(client.execute(command()));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(logins.get()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(afterFirst + 1);
        RawHttpRequest secondBusiness = transport.invocations().getLast();
        assertThat(secondBusiness.uri().getPath()).isEqualTo("/device/deviceList/v1.0");
        assertThat(header(secondBusiness, "Cookie")).isEqualTo("JSESSIONID=" + JSID);
        assertThat(header(secondBusiness, "Authorization")).isEmpty();
        assertNoSecretInTrace(second);
    }

    @Test
    void failedResultCodeDoesNotStormDuringCooldown() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/loginInfo/login/v1.0")) {
                logins.incrementAndGet();
                return json(200, "{\"resultCode\":\"1\"}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(logins.get()).isEqualTo(1);

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(logins.get()).isEqualTo(1);
        assertThat(header(transport.invocations().getLast(), "Cookie")).doesNotContain(JSID);
        assertNoSecretInTrace(cooldown);
    }

    @Test
    void omittedCookiePathDoesNotAttachOntoDeviceList() {
        URI login = URI.create("https://ivs.example/loginInfo/login/v1.0");
        URI device = URI.create("https://ivs.example/device/deviceList/v1.0");
        CookieStore omitted = new CookieStore();
        omitted.acceptSetCookie(login, List.of("JSESSIONID=" + JSID));
        assertThat(omitted.cookiesFor(device)).isEmpty();

        CookieStore rooted = new CookieStore();
        rooted.acceptSetCookie(login, List.of(SET_COOKIE));
        assertThat(rooted.cookiesFor(device)).isEqualTo("JSESSIONID=" + JSID);
    }

    private static Phase0ApiClient seeded(FakeTransport transport) {
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/huawei-ivs/username", USER);
        client.secrets().put("secret/huawei-ivs/password", PASS);
        client.loadPublished(ProtocolDefinitions.huaweiIvs());
        return client;
    }

    private static void assertNoSecretInTrace(ExecutionResult result) {
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(PASS, JSID, USER);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null
                        && (value.contains(PASS) || value.contains(JSID) || value.contains(USER)));
    }

    private static String header(RawHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "huawei-ivs",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        );
    }

    private static RawHttpResponse loginOk() {
        return new RawHttpResponse(
                200,
                Map.of("Set-Cookie", List.of(SET_COOKIE)),
                new ResponseBody.BytesBody(LOGIN_JSON.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
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
