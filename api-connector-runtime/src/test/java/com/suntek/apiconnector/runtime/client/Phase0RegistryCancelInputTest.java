/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.client;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.registry.DefinitionLifecycle;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Phase0RegistryCancelInputTest {

    @Test
    void publishedRevisionChangeDoesNotMutateInFlightSnapshot() throws Exception {
        FakeTransport transport = new FakeTransport()
                .enqueue(json(200, "{}"))
                .holdBeforeReturn(0);
        Phase0ApiClient client = new Phase0ApiClient(transport);
        ExecutionPlan first = client.loadPublished(mockA());
        ExecutionHandle handle = client.execute(command("mock-a"));
        String inFlightPlanId = handle.snapshot().planId();
        assertThat(inFlightPlanId).isEqualTo(first.planId());
        transport.awaitHold();
        ExecutionPlan second = client.loadPublished(mockA().replace("revision: 1", "revision: 2")
                .replace("/v1/status", "/v2/status"));
        assertThat(second.planId()).isNotEqualTo(first.planId());
        transport.releaseHold();
        ExecutionResult result = handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(handle.snapshot().planId()).isEqualTo(inFlightPlanId);
        assertThat(client.execute(command("mock-a")).snapshot().planId()).isEqualTo(second.planId());
    }

    @Test
    void draftExecuteIsNotPublishedAndSendsNoHttp() {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.load(mockA(), DefinitionLifecycle.DRAFT);
        assertThatThrownBy(() -> client.execute(command("mock-a")))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex).code())
                        .isEqualTo(ExecuteException.DEFINITION_NOT_PUBLISHED));
        assertThat(transport.invocations()).isEmpty();
    }

    @Test
    void cancelAfterFirstRequestSendsNoSecondRequest() throws Exception {
        FakeTransport transport = new FakeTransport()
                .enqueue(json(200, "{}"), json(200, "{}"))
                .holdBeforeReturn(0);
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(twoStepYaml());
        ExecutionHandle handle = client.execute(command("two-step"));
        transport.awaitHold();
        client.cancel(handle.executionId());
        transport.releaseHold();
        ExecutionResult result = handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.CANCELLED);
        assertThat(transport.invocations()).hasSize(1);
    }

    @Test
    void sessionInputIsRejectedWithoutSnapshotOrHttp() {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockA());
        assertThatThrownBy(() -> client.execute(new ExecuteCommand(
                "mock-a",
                "business",
                Map.of("session.token", new DataValue.StringValue("abc")),
                Optional.empty(),
                options()
        )))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex).code())
                        .isEqualTo(ValidationCodes.VAL_INPUT_SCOPE));
        assertThat(transport.invocations()).isEmpty();
    }

    @Test
    void secretLiteralInputIsRejectedWithoutHttp() {
        FakeTransport transport = new FakeTransport().enqueue(json(200, "{}"));
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockA());
        SecretValue secret = new SecretValue() {
            @Override
            public SecretMetadata metadata() {
                return new SecretMetadata("secret/mock-a/api-key", "mock-a");
            }

            @Override
            public void use(com.suntek.apiconnector.core.value.SecretConsumer consumer) {
                consumer.accept("leak".getBytes(StandardCharsets.UTF_8));
            }
        };
        assertThatThrownBy(() -> client.execute(new ExecuteCommand(
                "mock-a",
                "business",
                Map.of("apiKey", secret),
                Optional.empty(),
                options()
        )))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex).code())
                        .isEqualTo(ValidationCodes.VAL_SECRET_LITERAL));
        assertThat(transport.invocations()).isEmpty();
    }

    private static ExecuteCommand command(String apiId) {
        return new ExecuteCommand(apiId, "business", Map.of(), Optional.empty(), options());
    }

    private static ExecuteOptions options() {
        return new ExecuteOptions(Duration.ofSeconds(10), false, Map.of());
    }

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String mockA() {
        try (InputStream in = Phase0RegistryCancelInputTest.class.getResourceAsStream("/definitions/mock-a.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-a.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String twoStepYaml() {
        return """
                schema:
                  version: 1
                definition:
                  id: two-step
                  revision: 1
                  authProfile: none
                credentials:
                  apiKey:
                    type: secret
                    valueRef: secret/two-step/api-key
                    apiId: two-step
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://two.example"
                limits:
                  maxAuthAttempts: 0
                  maxAuthDepth: 0
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  first:
                    method: GET
                    url: "{global.baseUrl}/one"
                  second:
                    method: GET
                    url: "{global.baseUrl}/two"
                flows:
                  business:
                    steps:
                      - id: one
                        request: first
                        transitions:
                          - when: { status: 200 }
                            action: CONTINUE
                      - id: two
                        request: second
                        transitions:
                          - when: { status: 200 }
                            action: SUCCESS
                """;
    }
}
