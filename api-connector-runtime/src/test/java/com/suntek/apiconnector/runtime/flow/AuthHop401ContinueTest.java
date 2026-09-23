/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auth-hop HTTP 401 + CONTINUE then EXTRACT — not CHALLENGE / nested AUTHENTICATE.
 */
class AuthHop401ContinueTest {

    @Test
    void challenge401ContinueThenExtractBindsFieldsWithoutChallengeOutcome() throws Exception {
        AtomicInteger challenges = new AtomicInteger();
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                json(401, "{\"realm\":\"test-realm\",\"randomKey\":\"rk-1\",\"encryptType\":\"MD5\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(definition());

        ExecutionResult result = client.execute(command()).result().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(transport.invocations()).hasSize(3);
        assertThat(result.trace().decisions().stream().map(d -> d.type() + "/" + d.action()))
                .noneMatch(s -> s.contains("CHALLENGE"));
        assertThat(challenges.get()).isZero();
    }

    @Test
    void unmatched401OnAuthHopFailsWithoutStartingNestedAuthenticate() throws Exception {
        AtomicInteger authorizeCalls = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            if (request.uri().getPath().endsWith("/authorize")) {
                authorizeCalls.incrementAndGet();
                return json(401, "{\"error\":\"NO_CHALLENGE\"}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(definition());

        ExecutionResult result = client.execute(command()).result().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(authorizeCalls.get()).isEqualTo(1);
        assertThat(result.trace().decisions().stream().map(d -> d.type() + "/" + d.action()))
                .noneMatch(s -> s.contains("CHALLENGE"));
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "auth-hop-401",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of())
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

    private static String definition() {
        return """
                schema:
                  version: 1
                definition:
                  id: auth-hop-401
                  revision: 1
                  authProfile: challenge-continue
                credentials: {}
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://auth-hop.example"
                  realm:
                    type: string
                    scope: FLOW
                  randomKey:
                    type: string
                    scope: FLOW
                session:
                  ttl: 5m
                  failureCooldown: 5s
                limits:
                  maxAuthAttempts: 2
                  maxAuthDepth: 1
                  transitionLimit: 16
                  executionTimeout: 10s
                requests:
                  business:
                    method: GET
                    url: "{global.baseUrl}/business"
                    replay:
                      replayability: SAFE
                      allowAutomaticReplay: true
                      maxAttempts: 2
                  challenge:
                    method: POST
                    url: "{global.baseUrl}/authorize"
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
                      - id: business
                        request: business
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
                      - id: challenge
                        request: challenge
                        pipeline: identity
                        transitions:
                          - when:
                              all:
                                - status: 401
                                - jsonpath:
                                    path: $.randomKey
                                    exists: true
                                - jsonpath:
                                    path: $.realm
                                    exists: true
                            action: CONTINUE
                          - when:
                              status: 401
                            action: FAIL
                            session: AUTH_FAILED
                      - id: extractRealm
                        extract:
                          from:
                            jsonpath: $.realm
                          to: flow.realm
                      - id: extractRandomKey
                        extract:
                          from:
                            jsonpath: $.randomKey
                          to: flow.randomKey
                        onCommit:
                          sessionStatus: VALID
                          generation: increment
                """;
    }
}
