/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.MappingRule;

import javax.script.CompiledScript;
import java.util.List;

/**
 * Resolved per-direction mapping config after connector default and endpoint override merge (D-01).
 */
public final class ResolvedMapping {

    private final ResolvedDirection request;
    private final ResolvedDirection response;
    private final ResolvedDirection error;

    public ResolvedMapping(ResolvedDirection request, ResolvedDirection response, ResolvedDirection error) {
        this.request = request;
        this.response = response;
        this.error = error;
    }

    public static ResolvedMapping empty() {
        return new ResolvedMapping(null, null, null);
    }

    public static ResolvedMapping of(
            ResolvedDirection request,
            ResolvedDirection response,
            ResolvedDirection error) {
        return new ResolvedMapping(request, response, error);
    }

    public ResolvedDirection request() {
        return request;
    }

    public ResolvedDirection response() {
        return response;
    }

    public ResolvedDirection error() {
        return error;
    }

    public boolean hasRequest() {
        return request != null && request.isConfigured();
    }

    public boolean hasResponse() {
        return response != null && response.isConfigured();
    }

    public boolean hasError() {
        return error != null && error.isConfigured();
    }

    /**
     * Single direction block with declarative rules or Groovy script.
     */
    public static final class ResolvedDirection {

        private final List<MappingRule> rules;
        private final String script;
        private final CompiledScript compiledScript;

        private ResolvedDirection(List<MappingRule> rules, String script, CompiledScript compiledScript) {
            this.rules = rules;
            this.script = script;
            this.compiledScript = compiledScript;
        }

        public static ResolvedDirection from(DirectionMappingSpec direction) {
            if (direction == null) {
                return null;
            }
            return new ResolvedDirection(direction.rules(), direction.script(), null);
        }

        public static ResolvedDirection ofRules(List<MappingRule> rules) {
            return new ResolvedDirection(rules, null, null);
        }

        public static ResolvedDirection ofScript(String script, CompiledScript compiledScript) {
            return new ResolvedDirection(null, script, compiledScript);
        }

        public List<MappingRule> rules() {
            return rules;
        }

        public String script() {
            return script;
        }

        public CompiledScript compiledScript() {
            return compiledScript;
        }

        public boolean hasRules() {
            return rules != null && !rules.isEmpty();
        }

        public boolean hasScript() {
            return script != null && !script.isBlank();
        }

        public boolean isConfigured() {
            return hasRules() || hasScript();
        }
    }
}
