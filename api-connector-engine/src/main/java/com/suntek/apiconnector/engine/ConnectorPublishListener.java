/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.exception.AuthExceptions;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthScript;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.TransformStepRegistry;
import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.MappingScript;
import com.suntek.apiconnector.mapping.validation.MappingSpecValidator;
import com.suntek.apiconnector.scripting.ScriptCompileException;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingSpec;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publish-time hook: evict OAuth tokens, validate mapping, and pre-compile Groovy scripts (D-08, D-21, D-29).
 */
public final class ConnectorPublishListener {

    private static final String GROOVY_AUTH_SCRIPT = "groovy_auth_script";

    private final ScriptCompileService scriptCompileService;
    private final TokenCache tokenCache;
    private final TransformStepRegistry transformStepRegistry;

    public ConnectorPublishListener(ScriptCompileService scriptCompileService, TokenCache tokenCache) {
        this(scriptCompileService, tokenCache, null);
    }

    public ConnectorPublishListener(
            ScriptCompileService scriptCompileService,
            TokenCache tokenCache,
            TransformStepRegistry transformStepRegistry) {
        this.scriptCompileService = scriptCompileService;
        this.tokenCache = tokenCache;
        this.transformStepRegistry = transformStepRegistry;
    }

    /**
     * Evicts cached tokens and compiles all Groovy auth and mapping scripts in the connector spec.
     */
    public void onPublish(ConnectorSpec spec) {
        try {
            onPublishInternal(spec);
        } catch (ScriptCompileException ex) {
            if (isMappingLabel(ex.label())) {
                throw MappingExceptions.scriptCompileError(ex);
            }
            throw AuthExceptions.scriptCompileError(ex);
        }
    }

    private void onPublishInternal(ConnectorSpec spec) {
        validateMappingSpec(spec);

        tokenCache.evictForConnector(spec.code3rd());

        Set<String> compiledSources = new HashSet<>();

        if (spec.mapping() != null) {
            scanMappingSpec(spec.mapping(), spec.code3rd(), null, compiledSources);
        }

        List<EndpointSpec> endpoints = spec.endpoints();
        if (endpoints != null) {
            for (EndpointSpec endpoint : endpoints) {
                if (endpoint.mappingOverride() != null) {
                    scanMappingSpec(endpoint.mappingOverride(), spec.code3rd(), endpoint.id(), compiledSources);
                }
            }
        }

        scanAuthConfig(spec.auth(), spec.code3rd(), "auth", compiledSources);

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

    private void validateMappingSpec(ConnectorSpec spec) {
        try {
            new MappingSpecValidator(transformStepRegistry).validate(spec);
        } catch (IllegalArgumentException ex) {
            throw MappingExceptions.specInvalid("mapping", ex.getMessage(), ex);
        }
    }

    private void scanMappingSpec(
            MappingSpec mapping,
            String code3rd,
            String endpointId,
            Set<String> compiledSources) {
        compileMappingDirectionIfPresent(mapping.request(), code3rd, endpointId, "request", compiledSources);
        compileMappingDirectionIfPresent(mapping.response(), code3rd, endpointId, "response", compiledSources);
        compileMappingDirectionIfPresent(mapping.error(), code3rd, endpointId, "error", compiledSources);
    }

    private void compileMappingDirectionIfPresent(
            DirectionMappingSpec direction,
            String code3rd,
            String endpointId,
            String directionName,
            Set<String> compiledSources) {
        if (direction == null) {
            return;
        }
        String source = direction.script();
        if (source == null || source.isBlank()) {
            return;
        }
        if (!compiledSources.add(source)) {
            return;
        }
        String compileLabel = mappingCompileLabel(code3rd, endpointId, directionName);
        CompiledScript compiled = scriptCompileService.compile(source, compileLabel);
        validateMappingScriptContract(compiled, compileLabel);
    }

    private static String mappingCompileLabel(String code3rd, String endpointId, String directionName) {
        if (endpointId != null && !endpointId.isBlank()) {
            return code3rd + ":endpoint:" + endpointId + ":mapping:" + directionName;
        }
        return code3rd + ":mapping:" + directionName;
    }

    private void validateMappingScriptContract(CompiledScript compiled, String label) {
        try {
            SimpleBindings bindings = new SimpleBindings();
            MappingContext stub = stubMappingContext();
            bindings.put("ctx", stub);
            Object result = compiled.eval(bindings);
            if (result instanceof Map<?, ?> || result instanceof List<?>) {
                return;
            }
            if (result instanceof MappingScript mappingScript) {
                Object delegated = mappingScript.apply(stub);
                if (delegated instanceof Map<?, ?> || delegated instanceof List<?>) {
                    return;
                }
                throw mappingContractError(
                        label,
                        "MappingScript.apply(MappingContext) must return Map or List, got "
                                + (delegated == null ? "null" : delegated.getClass().getName()));
            }
            throw mappingContractError(
                    label,
                    "script must return Map, List, or implement MappingScript.apply(MappingContext), got "
                            + (result == null ? "null" : result.getClass().getName()));
        } catch (ScriptCompileException ex) {
            throw ex;
        } catch (ScriptException ex) {
            throw new ScriptCompileException(
                    label,
                    null,
                    "MAPPING_SCRIPT_COMPILE_ERROR: contract validation failed: " + ex.getMessage(),
                    ex);
        }
    }

    private static MappingContext stubMappingContext() {
        return new MappingContext(
                "STUB",
                MappingDirection.RESPONSE,
                "{\"sample\":true}",
                new AuthContextSnapshot("STUB", List.of(), Map.of(), Map.of()),
                new EndpointMeta("stub", "GET", "/"));
    }

    private static ScriptCompileException mappingContractError(String label, String message) {
        return new ScriptCompileException(label, null, "MAPPING_SCRIPT_COMPILE_ERROR: " + message, null);
    }

    private static boolean isMappingLabel(String label) {
        return label != null && label.contains(":mapping:");
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
