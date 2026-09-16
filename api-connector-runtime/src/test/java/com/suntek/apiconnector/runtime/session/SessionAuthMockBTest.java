/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.session.SessionKey;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.runtime.value.ByteSecret;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAuthMockBTest {

    @Test
    void mockBCompilesWithoutPipelineGraph() {
        var plan = com.suntek.apiconnector.runtime.compile.PlanCompiler.compile(mockB());
        assertThat(plan.capabilities()).contains(
                PlanCapability.LINEAR_FLOW,
                PlanCapability.AUTH_FLOW,
                PlanCapability.SESSION,
                PlanCapability.REPLAY
        );
        assertThat(plan.capabilities()).doesNotContain(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.credentialRef()).isEqualTo("account");
    }

    @Test
    void l1_authenticateThenReplayRendersTemplateNotClone() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                json(200, "{\"token\":\"t-1\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        ExecutionHandle handle = client.execute(command("mock-b"));
        String executionId = handle.executionId();
        ExecutionResult result = handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.httpStatus()).contains(200);
        assertThat(result.session()).isPresent();
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(result.session().orElseThrow().status()).isEqualTo("VALID");
        assertThat(handle.executionId()).isEqualTo(executionId);
        assertThat(transport.invocations()).hasSize(3);

        RawHttpRequest r1 = transport.invocations().get(0);
        RawHttpRequest r2 = transport.invocations().get(1);
        RawHttpRequest r3 = transport.invocations().get(2);
        assertThat(r1.method()).isEqualTo("GET");
        assertThat(r1.uri().getPath()).isEqualTo("/v1/data");
        assertThat(authorization(r1)).isEmpty();
        assertThat(r2.method()).isEqualTo("POST");
        assertThat(r2.uri().getPath()).isEqualTo("/v1/login");
        assertThat(r3.method()).isEqualTo("GET");
        assertThat(r3.uri().getPath()).isEqualTo("/v1/data");
        assertThat(authorization(r3)).contains("Bearer t-1");
        assertThat(r3).isNotSameAs(r1);
    }

    @Test
    void h2b_pipelineOnlyRevisionReusesSessionWithoutLogin() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = router(logins, "t-reuse");
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        assertThat(await(client.execute(command("mock-b"))).outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(logins.get()).isEqualTo(1);

        client.loadPublished(mockB()
                .replace("revision: 1", "revision: 2")
                .replace("id: passthrough", "id: passthrough-v2"));
        ExecutionResult second = await(client.execute(command("mock-b")));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(second.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(logins.get()).isEqualTo(1);
    }

    @Test
    void h3_authProfileChangeDoesNotReuseSession() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = router(logins, "t-new");
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        await(client.execute(command("mock-b")));
        client.loadPublished(mockB()
                .replace("revision: 1", "revision: 3")
                .replace("authProfile: password-login", "authProfile: password-login-v2"));
        ExecutionResult third = await(client.execute(command("mock-b")));
        assertThat(third.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(logins.get()).isEqualTo(2);
    }

    @Test
    void concurrentExpiredSessionsHaveOneAuthOwner() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = router(logins, "new-token");
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        client.sessions().seedValid(
                new SessionKey("mock-b", "1", "password-login", "account"),
                10,
                Map.of("token", ByteSecret.utf8(new SecretMetadata("session.token", "mock-b"), "old-token"))
        );
        List<ExecutionHandle> handles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            handles.add(client.execute(command("mock-b")));
        }
        for (ExecutionHandle handle : handles) {
            ExecutionResult result = await(handle);
            assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
            assertThat(result.session().orElseThrow().generation()).isEqualTo(11);
        }
        assertThat(logins.get()).isEqualTo(1);
        long replaysWithOld = transport.invocations().stream()
                .filter(req -> req.uri().getPath().equals("/v1/data"))
                .filter(req -> authorization(req).equals("Bearer old-token"))
                .count();
        assertThat(replaysWithOld).isLessThanOrEqualTo(100);
        long replaysWithNew = transport.invocations().stream()
                .filter(req -> req.uri().getPath().equals("/v1/data"))
                .filter(req -> authorization(req).equals("Bearer new-token"))
                .count();
        assertThat(replaysWithNew).isEqualTo(100);
    }

    @Test
    void mockG_sharedFailureAndCooldownSkipsSecondLogin() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/login")) {
                logins.incrementAndGet();
                return json(401, "{\"error\":\"DENIED\"}");
            }
            return json(401, "{\"error\":\"UNAUTHORIZED\"}");
        });
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        List<ExecutionHandle> handles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            handles.add(client.execute(command("mock-b")));
        }
        for (ExecutionHandle handle : handles) {
            assertThat(await(handle).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        }
        assertThat(logins.get()).isEqualTo(1);

        ExecutionResult cooldown = await(client.execute(command("mock-b")));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(logins.get()).isEqualTo(1);
    }

    @Test
    void maxAuthAttemptsExceededDoesNotStartAnotherAuthFlow() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                json(200, "{\"token\":\"t-1\"}"),
                json(401, "{\"error\":\"UNAUTHORIZED\"}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB().replace("maxAuthAttempts: 2", "maxAuthAttempts: 1"));
        ExecutionResult result = await(client.execute(command("mock-b")));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.AUTH_ATTEMPT_EXCEEDED);
        assertThat(transport.invocations()).hasSize(3);
        assertThat(transport.invocations().stream().filter(req -> req.uri().getPath().endsWith("/login"))).hasSize(1);
    }

    @Test
    void maxDepthExceededDoesNotStartNestedAuthFlow() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                json(401, "{\"error\":\"UNAUTHORIZED\"}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(nestedAuthYaml());
        ExecutionResult result = await(client.execute(command("mock-depth")));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.AUTH_ATTEMPT_EXCEEDED);
        assertThat(transport.invocations()).hasSize(2);
        assertThat(transport.invocations().get(1).uri().getPath()).isEqualTo("/v1/login");
    }

    private static FakeTransport router(AtomicInteger logins, String token) {
        return new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/login")) {
                logins.incrementAndGet();
                return json(200, "{\"token\":\"" + token + "\"}");
            }
            if (authorization(request).equals("Bearer " + token)) {
                return json(200, "{\"ok\":true}");
            }
            return json(401, "{\"error\":\"UNAUTHORIZED\"}");
        });
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command(String apiId) {
        return new ExecuteCommand(
                apiId,
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        );
    }

    private static String authorization(RawHttpRequest request) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Authorization") && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return "";
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String mockB() {
        return resource("/definitions/mock-b.yaml");
    }

    private static String resource(String path) {
        try (InputStream in = SessionAuthMockBTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nestedAuthYaml() {
        return """
                schema:
                  version: 1
                definition:
                  id: mock-depth
                  revision: 1
                  authProfile: nested
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://mock-depth.example"
                limits:
                  maxAuthAttempts: 4
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  getData:
                    method: GET
                    url: "{global.baseUrl}/v1/data"
                    replay:
                      replayability: SAFE
                      allowAutomaticReplay: true
                      maxAttempts: 1
                  login:
                    method: POST
                    url: "{global.baseUrl}/v1/login"
                    replay:
                      replayability: UNSAFE
                      allowAutomaticReplay: false
                      maxAttempts: 1
                pipelines:
                  identity:
                    nodes:
                      - id: passthrough
                        type: passthrough
                    edges: []
                flows:
                  business:
                    steps:
                      - id: getData
                        request: getData
                        pipeline: identity
                        transitions:
                          - when:
                              status: 401
                            action: AUTHENTICATE
                            then: REPLAY_REQUEST
                          - when:
                              status: 200
                            action: SUCCESS
                  authentication:
                    steps:
                      - id: login
                        request: login
                        pipeline: identity
                        transitions:
                          - when:
                              status: 401
                            action: AUTHENTICATE
                            then: FAIL
                          - when:
                              status: 200
                            action: CONTINUE
                """;
    }
}
