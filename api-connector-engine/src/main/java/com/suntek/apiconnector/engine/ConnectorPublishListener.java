/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.exception.AuthExceptions;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthScript;
import com.suntek.apiconnector.scripting.ScriptCompileException;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publish-time hook: evict OAuth tokens and pre-compile Groovy auth scripts (D-08, D-21).
 */
public final class ConnectorPublishListener {

    private static final String GROOVY_AUTH_SCRIPT = "groovy_auth_script";

    private final ScriptCompileService scriptCompileService;
    private final TokenCache tokenCache;

    public ConnectorPublishListener(ScriptCompileService scriptCompileService, TokenCache tokenCache) {
        this.scriptCompileService = scriptCompileService;
        this.tokenCache = tokenCache;
    }

    /**
     * Evicts cached tokens and compiles all Groovy auth scripts in the connector spec.
     */
    public void onPublish(ConnectorSpec spec) {
        try {
            onPublishInternal(spec);
        } catch (ScriptCompileException ex) {
            throw AuthExceptions.scriptCompileError(ex);
        }
    }

    private void onPublishInternal(ConnectorSpec spec) {
        tokenCache.evictForConnector(spec.code3rd());

        Set<String> compiledSources = new HashSet<>();
        scanAuthConfig(spec.auth(), spec.code3rd(), "auth", compiledSources);

        List<EndpointSpec> endpoints = spec.endpoints();
        if (endpoints != null) {
            for (EndpointSpec endpoint : endpoints) {
                if (endpoint.authOverride() != null) {
                    scanAuthConfig(
                            endpoint.authOverride(),
                            spec.code3rd(),
                            "endpoint:" + endpoint.id(),
                            compiledSources);
                }
            }
        }
    }

    private void scanAuthConfig(
            Map<String, Object> auth,
            String code3rd,
            String labelPrefix,
            Set<String> compiledSources) {
        if (auth == null || auth.isEmpty()) {
            return;
        }
        Object pipeline = auth.get("pipeline");
        if (pipeline instanceof List<?> steps) {
            for (int i = 0; i < steps.size(); i++) {
                Object step = steps.get(i);
                if (step instanceof Map<?, ?> stepMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stepConfig = (Map<String, Object>) stepMap;
                    compileGroovyIfPresent(stepConfig, code3rd, labelPrefix + ":pipeline:" + i, compiledSources);
                }
            }
            return;
        }
        compileGroovyIfPresent(auth, code3rd, labelPrefix, compiledSources);
    }

    private void compileGroovyIfPresent(
            Map<String, Object> config,
            String code3rd,
            String label,
            Set<String> compiledSources) {
        if (!GROOVY_AUTH_SCRIPT.equals(String.valueOf(config.get("type")))) {
            return;
        }
        Object scriptValue = config.get("script");
        if (scriptValue == null || String.valueOf(scriptValue).isBlank()) {
            throw new ScriptCompileException(
                    code3rd + ":" + label,
                    null,
                    "AUTH_SCRIPT_COMPILE_ERROR: missing script source for groovy_auth_script",
                    null);
        }
        String source = String.valueOf(scriptValue);
        if (!compiledSources.add(source)) {
            return;
        }
        String compileLabel = code3rd + ":" + label;
        CompiledScript compiled = scriptCompileService.compile(source, compileLabel);
        validateAuthScriptContract(compiled, compileLabel);
    }

    private void validateAuthScriptContract(CompiledScript compiled, String label) {
        try {
            SimpleBindings bindings = new SimpleBindings();
            AuthContext stub = stubContext();
            bindings.put("ctx", stub);
            Object result = compiled.eval(bindings);
            if (result instanceof AuthOutcome) {
                return;
            }
            if (result instanceof AuthScript authScript) {
                AuthOutcome outcome = authScript.apply(stub);
                if (outcome != null) {
                    return;
                }
                throw contractError(label, "AuthScript.apply(AuthContext) returned null");
            }
            throw contractError(
                    label,
                    "script must return AuthOutcome or implement AuthScript.apply(AuthContext), got "
                            + (result == null ? "null" : result.getClass().getName()));
        } catch (ScriptCompileException ex) {
            throw ex;
        } catch (ScriptException ex) {
            throw new ScriptCompileException(
                    label,
                    null,
                    "AUTH_SCRIPT_COMPILE_ERROR: contract validation failed: " + ex.getMessage(),
                    ex);
        }
    }

    private static AuthContext stubContext() {
        return new AuthContext(
                "STUB",
                "http://localhost",
                "GET",
                "/",
                Map.of(),
                null,
                Map.of("type", "none"),
                Map.of(),
                new HashMap<>());
    }

    private static ScriptCompileException contractError(String label, String message) {
        return new ScriptCompileException(label, null, "AUTH_SCRIPT_COMPILE_ERROR: " + message, null);
    }
}
