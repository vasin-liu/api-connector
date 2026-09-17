/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.api.ExecuteOptions;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.client.Phase0ApiClient;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.CompiledCredential;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.runtime.value.ByteSecret;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialResolverSecretTest {

    @Test
    void planStoresRefsNotResolvedMaterial() {
        ExecutionPlan plan = PlanCompiler.compile(mockB());
        CompiledCredential account = plan.credentials().get("account");
        assertThat(account.usernameRef()).contains("secret/mock-b/username");
        assertThat(account.passwordRef()).contains("secret/mock-b/password");
        assertThat(plan.toString()).doesNotContain("alice");
        assertThat(plan.toString()).doesNotContain("s3cret");
    }

    @Test
    void missingSecretIsUnresolvableBeforeLoginSend() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.registerPublished(PlanCompiler.compile(mockB()));
        ExecutionHandle handle = client.execute(command("mock-b"));
        assertThatThrownBy(() -> handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ExecuteException.class)
                .satisfies(ex -> assertThat(((ExecuteException) ex.getCause()).code())
                        .isEqualTo(ExecuteException.SECRET_UNRESOLVABLE));
        assertThat(transport.invocations()).hasSize(1);
        assertThat(transport.invocations().getFirst().uri().getPath()).isEqualTo("/v1/data");
    }

    @Test
    void mockBLoginBodyUsesResolvedSecretsAndTraceIsRedacted() throws Exception {
        FakeTransport transport = new FakeTransport().enqueue(
                json(401, "{\"error\":\"UNAUTHORIZED\"}"),
                json(200, "{\"token\":\"t-1\"}"),
                json(200, "{\"ok\":true}")
        );
        Phase0ApiClient client = new Phase0ApiClient(transport);
        client.loadPublished(mockB());
        client.secrets().put("secret/mock-b/username", "alice");
        client.secrets().put("secret/mock-b/password", "s3cret");
        ExecutionResult result = await(client.execute(command("mock-b")));
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        String loginBody = new String(transport.invocations().get(1).body().orElse(new byte[0]), StandardCharsets.UTF_8);
        assertThat(loginBody).contains("alice").contains("s3cret");
        String trace = String.valueOf(result.trace());
        assertThat(trace).doesNotContain("s3cret");
        assertThat(trace.toLowerCase()).doesNotContain("bearer t-1");
    }

    @Test
    void fileProviderReadsFileRef(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("key.txt");
        Files.writeString(file, "file-material");
        FileSecretProvider files = new FileSecretProvider(dir);
        CredentialResolver resolver = new CredentialResolver(List.of(files));
        SecretValue secret = resolver.require("file:" + file.toAbsolutePath(), "mock-c");
        String[] holder = new String[1];
        secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
        assertThat(holder[0]).isEqualTo("file-material");
        assertThat(secret.toString()).doesNotContain("file-material");
    }

    @Test
    void secretValueHasNoGenericReveal() {
        SecretValue secret = ByteSecret.utf8(new SecretMetadata("secret/x", "api"), "top-secret");
        assertThat(secret.toString()).doesNotContain("top-secret");
        assertThat(SecretValue.class.getMethods())
                .noneMatch(method -> "reveal".equals(method.getName()));
    }

    private static ExecutionResult await(ExecutionHandle handle) throws Exception {
        return handle.result().toCompletableFuture().get(5, TimeUnit.SECONDS);
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

    private static RawHttpResponse json(int status, String body) {
        return new RawHttpResponse(
                status,
                Map.of(),
                new ResponseBody.BytesBody(body.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                true
        );
    }

    private static String mockB() {
        try (InputStream in = CredentialResolverSecretTest.class.getResourceAsStream("/definitions/mock-b.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-b.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
