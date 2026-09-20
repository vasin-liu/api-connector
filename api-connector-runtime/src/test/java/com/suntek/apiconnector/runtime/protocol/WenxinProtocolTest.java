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
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WenxinProtocolTest {

    private static final String TOKEN = "test-wenxin-token";
    private static final String CLIENT_ID = "test-wenxin-id";
    private static final String CLIENT_SECRET = "test-wenxin-secret";

    @Test
    void twoCompilesProduceTheSamePlanId() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.wenxin());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.wenxin());
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId()).matches("[0-9a-f]{64}");
        assertThat(first.definitionId()).isEqualTo("baidu-wenxin");
    }

    @Test
    void firstBusiness401ThenOneTokenThenReplayWithQueryToken() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                json(200, "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":2592000}"),
                json(200, "{\"result\":\"ok\"}")
        );
        Phase0ApiClient client = seeded(transport);
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(3);

        RawHttpRequest business1 = transport.invocations().get(0);
        RawHttpRequest token = transport.invocations().get(1);
        RawHttpRequest replay = transport.invocations().get(2);
        assertThat(business1.method()).isEqualTo("POST");
        assertThat(business1.uri().getPath()).isEqualTo("/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/eb-instant");
        assertThat(query(business1)).doesNotContain("access_token");
        assertThat(token.method()).isEqualTo("GET");
        assertThat(token.uri().getPath()).isEqualTo("/oauth/2.0/token");
        assertThat(query(token)).contains("grant_type=client_credentials");
        assertThat(query(token)).contains("client_id=" + CLIENT_ID);
        assertThat(replay.uri().getQuery()).contains("access_token=" + TOKEN);
        assertThat(replay).isNotSameAs(business1);
        assertNoSecretInTrace(result);
    }

    @Test
    void validSessionSkipsTokenEndpoint() throws Exception {
        AtomicInteger tokens = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/oauth/2.0/token")) {
                tokens.incrementAndGet();
                return json(200, "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":2592000}");
            }
            if (query(request).contains("access_token=" + TOKEN)) {
                return json(200, "{\"result\":\"ok\"}");
            }
            return json(401, "{\"error\":\"UNAUTHORIZED\"}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(tokens.get()).isEqualTo(1);
        int afterFirst = transport.invocations().size();

        ExecutionResult second = await(client.execute(command()));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(tokens.get()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(afterFirst + 1);
        assertThat(transport.invocations().getLast().uri().getPath())
                .isEqualTo("/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/eb-instant");
        assertNoSecretInTrace(second);
    }

    @Test
    void failedTokenDoesNotStormDuringCooldown() throws Exception {
        AtomicInteger tokens = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/oauth/2.0/token")) {
                tokens.incrementAndGet();
                return json(401, "{\"error\":\"DENIED\"}");
            }
            return json(401, "{\"error\":\"UNAUTHORIZED\"}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(tokens.get()).isEqualTo(1);

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(tokens.get()).isEqualTo(1);
        assertNoSecretInTrace(cooldown);
    }

    private static Phase0ApiClient seeded(FakeTransport transport) {
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/baidu-wenxin/client-id", CLIENT_ID);
        client.secrets().put("secret/baidu-wenxin/client-secret", CLIENT_SECRET);
        client.loadPublished(ProtocolDefinitions.wenxin());
        return client;
    }

    private static void assertNoSecretInTrace(ExecutionResult result) {
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(TOKEN, CLIENT_ID, CLIENT_SECRET);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null
                        && (value.contains(TOKEN) || value.contains(CLIENT_ID) || value.contains(CLIENT_SECRET)));
    }

    private static String query(RawHttpRequest request) {
        String q = request.uri().getQuery();
        return q == null ? "" : q;
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "baidu-wenxin",
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
