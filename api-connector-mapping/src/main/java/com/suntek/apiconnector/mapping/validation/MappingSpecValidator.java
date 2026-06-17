/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.validation;

import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.mapping.TransformStepRegistry;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingOps;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;

import java.util.List;

/**
 * Publish-time validation for declarative mapping rules and {@code transform[]} steps (D-04, D-10, D-30).
 */
public final class MappingSpecValidator {

    private final TransformStepRegistry transformStepRegistry;

    public MappingSpecValidator() {
        this(null);
    }

    public MappingSpecValidator(TransformStepRegistry transformStepRegistry) {
        this.transformStepRegistry = transformStepRegistry;
    }

    public void validate(ConnectorSpec spec) {
        if (spec == null) {
            return;
        }
        if (spec.mapping() != null) {
            validateMappingSpec(spec.mapping(), "mapping");
        }
        if (spec.endpoints() != null) {
            for (EndpointSpec endpoint : spec.endpoints()) {
                if (endpoint.mappingOverride() != null) {
                    String prefix = "endpoints[" + endpoint.id() + "].mappingOverride";
                    validateMappingSpec(endpoint.mappingOverride(), prefix);
                }
            }
        }
    }

    private static void validateMappingSpec(MappingSpec mapping, String pathPrefix) {
        validateDirection(mapping.request(), pathPrefix + ".request");
        validateDirection(mapping.response(), pathPrefix + ".response");
        validateDirection(mapping.error(), pathPrefix + ".error");
    }

    private static void validateDirection(DirectionMappingSpec direction, String directionPath) {
        if (direction == null) {
            return;
        }
        boolean hasRules = direction.rules() != null && !direction.rules().isEmpty();
        boolean hasScript = direction.script() != null && !direction.script().isBlank();
        if (hasRules && hasScript) {
            throw new IllegalArgumentException(
                    directionPath + " cannot contain both rules and script (D-10)");
        }
        if (hasRules) {
            validateRules(direction.rules(), directionPath + ".rules");
        }
    }

    private static void validateRules(List<MappingRule> rules, String rulesPath) {
        for (int i = 0; i < rules.size(); i++) {
            validateRule(rules.get(i), rulesPath + "[" + i + "]");
        }
    }

    private static void validateRule(MappingRule rule, String rulePath) {
        String op = rule.op();
        if (op == null || op.isBlank()) {
            throw new IllegalArgumentException(rulePath + ".op is required");
        }
        if (!MappingOps.KNOWN.contains(op)) {
            throw new IllegalArgumentException(rulePath + ".op unknown transform: " + op);
        }
        switch (op) {
            case MappingOps.SET -> validateSetRule(rule, rulePath);
            case MappingOps.RENAME, MappingOps.COERCE, MappingOps.NEST -> validateSourceTargetRule(rule, rulePath, op);
            case MappingOps.ARRAY_MAP -> validateArrayMapRule(rule, rulePath);
            default -> throw new IllegalArgumentException(rulePath + ".op unknown transform: " + op);
        }
    }

    private static void validateSetRule(MappingRule rule, String rulePath) {
        if (rule.source() != null && !rule.source().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".source must not be set for op set (D-08)");
        }
        if (rule.target() == null || rule.target().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".target is required for op set");
        }
        if (rule.value() == null) {
            throw new IllegalArgumentException(rulePath + ".value is required for op set");
        }
        compileJsonPath(rule.target(), rulePath + ".target");
    }

    private static void validateSourceTargetRule(MappingRule rule, String rulePath, String op) {
        if (rule.source() == null || rule.source().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".source is required for op " + op);
        }
        if (rule.target() == null || rule.target().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".target is required for op " + op);
        }
        compileJsonPath(rule.source(), rulePath + ".source");
        compileJsonPath(rule.target(), rulePath + ".target");
        if (MappingOps.COERCE.equals(op)) {
            if (rule.type() == null || rule.type().isBlank()) {
                throw new IllegalArgumentException(rulePath + ".type is required for op coerce (D-06)");
            }
            if (!MappingOps.COERCE_TYPES.contains(rule.type())) {
                throw new IllegalArgumentException(
                        rulePath + ".type must be one of string|number|boolean|date, got: " + rule.type());
            }
        }
    }

    private static void validateArrayMapRule(MappingRule rule, String rulePath) {
        if (rule.source() == null || rule.source().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".source is required for op array_map");
        }
        if (rule.target() == null || rule.target().isBlank()) {
            throw new IllegalArgumentException(rulePath + ".target is required for op array_map");
        }
        compileJsonPath(rule.source(), rulePath + ".source");
        compileJsonPath(rule.target(), rulePath + ".target");
        if (rule.rules() == null || rule.rules().isEmpty()) {
            throw new IllegalArgumentException(rulePath + ".rules is required for op array_map");
        }
        validateRules(rule.rules(), rulePath + ".rules");
    }

    private static void compileJsonPath(String path, String fieldPath) {
        try {
            JsonPath.compile(normalizePath(path));
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    fieldPath + " invalid JSONPath: " + path + " — " + ex.getMessage(), ex);
        }
    }

    private static String normalizePath(String path) {
        if (path.startsWith("$")) {
            return path;
        }
        return "$." + path;
    }
}
