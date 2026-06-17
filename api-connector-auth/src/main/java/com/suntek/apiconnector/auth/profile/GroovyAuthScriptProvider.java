/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.profile;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.exception.AuthExceptions;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthProvider;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.scripting.ScriptCompileException;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.Map;

/**
 * AuthProvider adapter for {@code groovy_auth_script} profile type.
 */
public class GroovyAuthScriptProvider implements AuthProvider {

    private static final String TYPE = "groovy_auth_script";

    private final ScriptCompileService scriptCompileService;

    public GroovyAuthScriptProvider(ScriptCompileService scriptCompileService) {
        this.scriptCompileService = scriptCompileService;
    }

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String source = stringConfig(context.authConfig(), "script", null);
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("Missing required auth config key: script");
        }
        try {
            CompiledScript compiled = scriptCompileService.compile(source, context.code3rd());
            SimpleBindings bindings = new SimpleBindings();
            bindings.put("ctx", context);
            Object result = compiled.eval(bindings);
            if (!(result instanceof AuthOutcome outcome)) {
                throw new ClassCastException(
                        "Groovy auth script must return AuthOutcome, got: "
                                + (result == null ? "null" : result.getClass().getName()));
            }
            return outcome;
        } catch (ScriptCompileException ex) {
            throw AuthExceptions.scriptCompileError(ex);
        } catch (ClassCastException ex) {
            throw AuthExceptions.scriptRuntimeError(
                    "Groovy auth script must return AuthOutcome: " + ex.getMessage(), context.code3rd());
        } catch (ScriptException ex) {
            throw AuthExceptions.scriptRuntimeError(
                    "Groovy auth script execution failed: " + ex.getMessage(), context.code3rd());
        }
    }

    private static String stringConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
