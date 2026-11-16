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
import com.suntek.apiconnector.runtime.pipeline.RsaPkcs1CipherTest;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficDahuaTcplProtocolTest {

    private static final String USER = "test-user";
    private static final String PASS = "test-pass";
    private static final String TOKEN = "test-token";
    private static final String SESSION_ID = "test-session-id";
    private static final String S2 = RsaPkcs1CipherTest.S2;
    private static final String SPKI = RsaPkcs1CipherTest.SPKI;
    private static final String PUBLIC_KEY_JSON =
            "{\"publicKey\":\"" + SPKI + "\",\"subjectSessionId\":\"" + SESSION_ID + "\"}";
    private static final String TOKEN_JSON = "{\"token\":\"" + TOKEN + "\"}";
    private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    void twoCompilesProduceTheSamePlanIdDistinctFromTrafficDahua() {
        ExecutionPlan first = PlanCompiler.compile(ProtocolDefinitions.dahuaTcpl());
        ExecutionPlan second = PlanCompiler.compile(ProtocolDefinitions.dahuaTcpl());
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId()).matches("[0-9a-f]{64}");
        assertThat(first.definitionId()).isEqualTo("dahua-tcpl");
        assertThat(first.planId())
                .isNotEqualTo(PlanCompiler.compile(ProtocolDefinitions.trafficDahua()).planId());

        CompiledRequest business = first.requests().get("stub");
        assertThat(business.headers()).anyMatch(binding ->
                "X-Subject-Token".equalsIgnoreCase(binding.name())
                        && binding.sink().orElse(null) == SecretSink.HEADER);
        assertThat(first.pipelines()).containsKeys("hashS1", "hashS2", "encryptPassword");
        assertThat(types(first, "encryptPassword"))
                .contains("canonicalizer.concat", "cipher.rsa-pkcs1")
                .doesNotContain("cipher.dahua-rsa");
        assertThat(ProtocolDefinitions.dahuaTcpl()).contains("type: cipher.rsa-pkcs1");
        assertThat(ProtocolDefinitions.dahuaTcpl()).doesNotContain("type: cipher.dahua-rsa");
    }

    @Test
    void unknownVendorNodeFailsCompileWithZeroHttp() {
        String yaml = ProtocolDefinitions.dahuaTcpl().replace("cipher.rsa-pkcs1", "cipher.dahua-rsa");
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(ValidationCodes.VAL_PIPE_UNKNOWN_NODE));
    }

    @Test
    void firstBusiness401ThenPublicKeyLoginAndSubjectTokenReplay() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{}"),
                json(200, PUBLIC_KEY_JSON),
                json(200, TOKEN_JSON),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = seeded(transport);
        ExecutionResult result = await(client.execute(command()));

        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.session().orElseThrow().generation()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(4);

        RawHttpRequest business1 = transport.invocations().get(0);
        RawHttpRequest publicKey = transport.invocations().get(1);
        RawHttpRequest login = transport.invocations().get(2);
        RawHttpRequest replay = transport.invocations().get(3);

        assertThat(business1.method()).isEqualTo("GET");
        assertThat(business1.uri().getPath()).isEqualTo("/jg/tcpl/stub");
        assertThat(header(business1, "X-Subject-Token")).isEmpty();

        assertThat(publicKey.method()).isEqualTo("GET");
        assertThat(publicKey.uri().getPath()).isEqualTo("/jg/AuthPlatform/accounts/publicKey");

        assertThat(login.method()).isEqualTo("POST");
        assertThat(login.uri().getPath()).isEqualTo("/jg/AuthPlatform/accounts/login");
        assertThat(header(login, "Content-Type")).isEqualTo("application/json;charset=UTF-8");
        assertThat(header(login, "X-Subject-SessionId")).isEqualTo(SESSION_ID);

        Map<String, String> fields = jsonObject(body(login));
        assertThat(new ArrayList<>(fields.keySet()))
                .containsExactly("userName", "password", "publicKey", "subjectSessionId");
        assertThat(fields.get("userName")).isEqualTo(USER);
        assertThat(fields.get("publicKey")).isEqualTo(SPKI);
        assertThat(fields.get("subjectSessionId")).isEqualTo(SESSION_ID);
        assertThat(RsaPkcs1CipherTest.decrypt(fields.get("password"))).isEqualTo(S2);
        assertThat(body(login)).doesNotContain(PASS);
        assertThat(wire(login)).doesNotContain(PASS);

        assertThat(replay.uri().getPath()).isEqualTo("/jg/tcpl/stub");
        assertThat(header(replay, "X-Subject-Token")).isEqualTo(TOKEN);
        assertThat(header(replay, "Authorization")).isEmpty();
        assertNoSecretLeak(result);
    }

    @Test
    void twoLoginsDecryptToSameS2() throws Exception {
        List<String> passwords = new ArrayList<>();
        FakeTransport transport = new FakeTransport().route(request -> {
            String path = request.uri().getPath();
            if (path.endsWith("/jg/tcpl/stub")) {
                if (TOKEN.equals(header(request, "X-Subject-Token"))) {
                    return json(200, "{\"ok\":true}");
                }
                return json(401, "{}");
            }
            if (path.endsWith("/accounts/publicKey")) {
                return json(200, PUBLIC_KEY_JSON);
            }
            if (path.endsWith("/accounts/login")) {
                passwords.add(jsonObject(body(request)).get("password"));
                return json(200, TOKEN_JSON);
            }
            return json(500, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);

        // Force a second authentication by clearing session via a new client with same transport state.
        Phase0ApiClient secondClient = seeded(new FakeTransport().route(request -> {
            String path = request.uri().getPath();
            if (path.endsWith("/jg/tcpl/stub")) {
                if (TOKEN.equals(header(request, "X-Subject-Token"))) {
                    return json(200, "{\"ok\":true}");
                }
                return json(401, "{}");
            }
            if (path.endsWith("/accounts/publicKey")) {
                return json(200, PUBLIC_KEY_JSON);
            }
            if (path.endsWith("/accounts/login")) {
                passwords.add(jsonObject(body(request)).get("password"));
                return json(200, TOKEN_JSON);
            }
            return json(500, "{}");
        }));
        assertThat(await(secondClient.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);

        assertThat(passwords).hasSize(2);
        assertThat(RsaPkcs1CipherTest.decrypt(passwords.get(0))).isEqualTo(S2);
        assertThat(RsaPkcs1CipherTest.decrypt(passwords.get(1))).isEqualTo(S2);
    }

    @Test
    void validSessionSkipsPublicKeyAndLogin() throws Exception {
        AtomicInteger publicKey = new AtomicInteger();
        AtomicInteger login = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            String path = request.uri().getPath();
            if (path.endsWith("/accounts/publicKey")) {
                publicKey.incrementAndGet();
                return json(200, PUBLIC_KEY_JSON);
            }
            if (path.endsWith("/accounts/login")) {
                login.incrementAndGet();
                return json(200, TOKEN_JSON);
            }
            if (TOKEN.equals(header(request, "X-Subject-Token"))) {
                return json(200, "{\"ok\":true}");
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(publicKey.get()).isEqualTo(1);
        assertThat(login.get()).isEqualTo(1);
        int afterFirst = transport.invocations().size();

        ExecutionResult second = await(client.execute(command()));
        assertThat(second.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(publicKey.get()).isEqualTo(1);
        assertThat(login.get()).isEqualTo(1);
        assertThat(transport.invocations()).hasSize(afterFirst + 1);
        assertThat(header(transport.invocations().getLast(), "X-Subject-Token")).isEqualTo(TOKEN);
        assertNoSecretLeak(second);
    }

    @Test
    void missingPublicKeyDoesNotStormLoginDuringCooldown() throws Exception {
        AtomicInteger publicKey = new AtomicInteger();
        AtomicInteger login = new AtomicInteger();
        FakeTransport transport = new FakeTransport().route(request -> {
            String path = request.uri().getPath();
            if (path.endsWith("/accounts/publicKey")) {
                publicKey.incrementAndGet();
                return json(200, "{\"subjectSessionId\":\"" + SESSION_ID + "\"}");
            }
            if (path.endsWith("/accounts/login")) {
                login.incrementAndGet();
                return json(200, TOKEN_JSON);
            }
            return json(401, "{}");
        });
        Phase0ApiClient client = seeded(transport);
        assertThat(await(client.execute(command())).outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(publicKey.get()).isEqualTo(1);
        assertThat(login.get()).isEqualTo(0);

        ExecutionResult cooldown = await(client.execute(command()));
        assertThat(cooldown.outcome()).isEqualTo(StepOutcomeType.FAILURE);
        assertThat(publicKey.get()).isEqualTo(1);
        assertThat(login.get()).isEqualTo(0);
        assertThat(header(transport.invocations().getLast(), "X-Subject-Token")).isEmpty();
        assertNoSecretLeak(cooldown);
    }

    private static List<String> types(ExecutionPlan plan, String pipelineId) {
        return plan.pipelines().get(pipelineId).nodes().stream()
                .map(node -> String.valueOf(YamlMaps.map(node).get("type")))
                .toList();
    }

    private static Phase0ApiClient seeded(FakeTransport transport) {
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.secrets().put("secret/dahua-tcpl/username", USER);
        client.secrets().put("secret/dahua-tcpl/password", PASS);
        client.loadPublished(ProtocolDefinitions.dahuaTcpl());
        return client;
    }

    private static void assertNoSecretLeak(ExecutionResult result) {
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain(PASS, USER);
        assertThat(result.trace().decisions().stream().flatMap(d -> d.facts().values().stream()))
                .noneMatch(value -> value != null && (value.contains(PASS) || value.contains(USER)));
    }

    private static Map<String, String> jsonObject(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = FIELD.matcher(json);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        for (String key : keys) {
            Pattern value = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
            Matcher valueMatcher = value.matcher(json);
            assertThat(valueMatcher.find()).as("missing field %s in %s", key, json).isTrue();
            fields.put(key, valueMatcher.group(1));
        }
        return fields;
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

    private static String wire(RawHttpRequest request) {
        return request.uri() + " " + request.headers() + " " + body(request);
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private static ExecuteCommand command() {
        return new ExecuteCommand(
                "dahua-tcpl",
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

