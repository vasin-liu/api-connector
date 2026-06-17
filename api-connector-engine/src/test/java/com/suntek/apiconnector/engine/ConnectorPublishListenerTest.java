package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.cache.CachedToken;
import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.auth.cache.TokenCacheKey;
import com.suntek.apiconnector.auth.exception.AuthErrorCode;
import com.suntek.apiconnector.auth.exception.AuthException;
import com.suntek.apiconnector.scripting.CompiledScriptCache;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorPublishListenerTest {

    @Test
    void onPublishCompilesGroovyScriptsAndEvictsTokens() {
        CompiledScriptCache scriptCache = new CompiledScriptCache();
        ScriptCompileService compileService = new ScriptCompileService(scriptCache);
        TokenCache tokenCache = new TokenCache();
        ConnectorPublishListener listener = new ConnectorPublishListener(compileService, tokenCache);

        TokenCacheKey key = new TokenCacheKey("GROOVY_DEMO", "oauth2_client_credentials", "");
        tokenCache.getOrRefresh(key, () -> new CachedToken("seed", Instant.now().plusSeconds(3600)));

        String script = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer publish-token'], [:], null)
                """;
        ConnectorSpec spec = groovyConnectorSpec("GROOVY_DEMO", script, null);

        listener.onPublish(spec);

        assertEquals(1, scriptCache.size());
        AtomicInteger fetchCount = new AtomicInteger();
        tokenCache.getOrRefresh(key, () -> {
            fetchCount.incrementAndGet();
            return new CachedToken("after-evict", Instant.now().plusSeconds(3600));
        });
        assertEquals(1, fetchCount.get());
    }

    @Test
    void onPublishCompilesEndpointAuthOverrideScripts() {
        CompiledScriptCache scriptCache = new CompiledScriptCache();
        ScriptCompileService compileService = new ScriptCompileService(scriptCache);
        ConnectorPublishListener listener = new ConnectorPublishListener(compileService, new TokenCache());

        String connectorScript = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer connector'], [:], null)
                """;
        String endpointScript = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer endpoint'], [:], null)
                """;
        ConnectorSpec spec = groovyConnectorSpec(
                "GROOVY_DEMO",
                connectorScript,
                Map.of(
                        "special",
                        new EndpointSpec(
                                "special",
                                "GET",
                                "/special",
                                null,
                                true,
                                null,
                                Map.of("type", "groovy_auth_script", "script", endpointScript))));

        listener.onPublish(spec);

        assertEquals(2, scriptCache.size());
    }

    @Test
    void onPublishRejectsScriptsMissingAuthScriptContract() {
        ScriptCompileService compileService = new ScriptCompileService();
        ConnectorPublishListener listener = new ConnectorPublishListener(compileService, new TokenCache());

        ConnectorSpec spec = groovyConnectorSpec(
                "BAD_SCRIPT",
                "return 'not-an-auth-outcome'",
                null);

        AuthException ex = assertThrows(AuthException.class, () -> listener.onPublish(spec));
        assertEquals(AuthErrorCode.AUTH_SCRIPT_COMPILE_ERROR, ex.code());
        assertTrue(ex.getMessage().contains("AUTH_SCRIPT_COMPILE_ERROR"));
    }

    @Test
    void registrySaveInvokesPublishListener() {
        CompiledScriptCache scriptCache = new CompiledScriptCache();
        ScriptCompileService compileService = new ScriptCompileService(scriptCache);
        TokenCache tokenCache = new TokenCache();
        ConnectorPublishListener listener = new ConnectorPublishListener(compileService, tokenCache);
        ConnectorRegistry registry = new ConnectorRegistry(listener);

        String script = """
                import com.suntek.apiconnector.domain.model.AuthOutcome
                new AuthOutcome([Authorization: 'Bearer registry'], [:], null)
                """;
        registry.save(groovyConnectorSpec("REGISTRY_DEMO", script, null), Map.of(), ConnectorSpecStatus.PUBLISHED);

        assertEquals(1, scriptCache.size());
    }

    // ROADMAP SC#2: compile-once on second invoke — defer full assertion to Plan 06 InvokeIntegrationTest.

    private static ConnectorSpec groovyConnectorSpec(
            String code3rd, String script, Map<String, EndpointSpec> endpointOverrides) {
        List<EndpointSpec> endpoints = List.of(new EndpointSpec("invoke", "GET", "/api/demo", null, true));
        if (endpointOverrides != null) {
            endpoints = endpointOverrides.values().stream().toList();
        }
        return new ConnectorSpec(
                code3rd,
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "groovy_auth_script", "script", script),
                endpoints,
                new ResponseSpec("$.success == true", "$.obj", "$.msg", "$.code"),
                null,
                null,
                null);
    }
}
