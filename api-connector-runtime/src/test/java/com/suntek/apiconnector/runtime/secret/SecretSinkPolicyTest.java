/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.session.SessionCoordinator;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.runtime.value.ByteSecret;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretSinkPolicyTest {

    @Test
    void wrongApiAuthorizationIsDenied() {
        SecretSinkPolicy policy = new SecretSinkPolicy();
        SecretValue secret = ByteSecret.utf8(new SecretMetadata("secret/a/key", "api-a"), "k");
        assertThatThrownBy(() -> policy.check(secret, SecretSink.AUTHORIZATION, new SinkDestination("api-b")))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex).code())
                        .isEqualTo(ExecuteException.SECRET_SINK_DENIED));
    }

    @Test
    void hmacSinkAllowedWhenApiMatches() {
        SecretSinkPolicy policy = new SecretSinkPolicy();
        SecretValue secret = ByteSecret.utf8(new SecretMetadata("secret/mock-c/api-key", "mock-c"), "k");
        policy.check(secret, SecretSink.HMAC, new SinkDestination("mock-c"));
    }

    @Test
    void executeDeniesWrongApiAuthorizationBeforeSend() {
        FakeTransport transport = new FakeTransport();
        SessionCoordinator sessions = new SessionCoordinator();
        Phase0ApiClient client = new Phase0ApiClient(
                new com.suntek.apiconnector.runtime.registry.InMemoryDefinitionRegistry(),
                transport,
                sessions
        );
        ExecutionPlan plan = client.loadPublished(mockB());
        sessions.seedValid(
                plan.sessionKey(),
                1L,
                Map.of("token", ByteSecret.utf8(new SecretMetadata("session.token", "other-api"), "t-1"))
        );
        assertThatThrownBy(() -> client.execute(new ExecuteCommand(
                "mock-b",
                "business",
                Map.of(),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(15), false, Map.of())
        )).result().toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex.getCause()).code())
                        .isEqualTo(ExecuteException.SECRET_SINK_DENIED));
        assertThat(transport.invocations()).isEmpty();
    }

    private static String mockB() {
        try (InputStream in = SecretSinkPolicyTest.class.getResourceAsStream("/definitions/mock-b.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-b.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
