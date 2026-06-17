/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.MappingScript;
import com.suntek.apiconnector.scripting.ScriptCompileException;
import com.suntek.apiconnector.scripting.ScriptCompileService;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.List;
import java.util.Map;

/**
 * Compile-once Groovy mapping script adapter (D-09, D-11, D-13).
 */
public final class GroovyMappingScriptProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ScriptCompileService scriptCompileService;

    public GroovyMappingScriptProvider(ScriptCompileService scriptCompileService) {
        this.scriptCompileService = scriptCompileService;
    }

    /**
     * Compiles (or reuses cached compile) and evaluates the script with {@code ctx} bound.
     */
    public Object apply(MappingContext context, String scriptSource, String compileLabel) {
        try {
            CompiledScript compiled = scriptCompileService.compile(scriptSource, compileLabel);
            return evalCompiled(compiled, context, compileLabel);
        } catch (ScriptCompileException ex) {
            throw MappingExceptions.scriptCompileError(ex);
        }
    }

    /**
     * Evaluates a pre-compiled script and returns the normalized Map or List result.
     */
    public Object evalCompiled(CompiledScript compiled, MappingContext context, String compileLabel) {
        try {
            SimpleBindings bindings = new SimpleBindings();
            bindings.put("ctx", context);
            Object result = compiled.eval(bindings);
            return normalizeResult(result, context, compileLabel);
        } catch (ScriptException ex) {
            throw MappingExceptions.scriptRuntimeError(
                    "Groovy mapping script execution failed: " + ex.getMessage(), context.code3rd());
        }
    }

    /**
     * Evaluates script output and serializes Map/List to JSON.
     */
    public String applyAsJson(MappingContext context, String scriptSource, String compileLabel) {
        return toJson(apply(context, scriptSource, compileLabel));
    }

    /**
     * Evaluates pre-compiled script output and serializes Map/List to JSON.
     */
    public String evalAsJson(CompiledScript compiled, MappingContext context, String compileLabel) {
        return toJson(evalCompiled(compiled, context, compileLabel));
    }

    private Object normalizeResult(Object result, MappingContext context, String compileLabel) {
        if (result instanceof Map<?, ?> || result instanceof List<?>) {
            return result;
        }
        if (result instanceof MappingScript mappingScript) {
            return normalizeResult(mappingScript.apply(context), context, compileLabel);
        }
        String typeName = result == null ? "null" : result.getClass().getName();
        throw MappingExceptions.scriptRuntimeError(
                "Groovy mapping script must return Map, List, or MappingScript, got: " + typeName,
                context.code3rd());
    }

    private String toJson(Object result) {
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize mapping script result: " + ex.getMessage(), ex);
        }
    }
}
