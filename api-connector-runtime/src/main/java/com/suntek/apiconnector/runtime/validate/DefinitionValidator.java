/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.validate;

import com.suntek.apiconnector.core.flow.condition.JsonPathConditionSupport;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.core.validate.Violation;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static semantic checks (table U1–U3 subset needed for Mock A and AUTHENTICATE).
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class DefinitionValidator {

    private DefinitionValidator() {
    }

    /**
     * @param normalized Normalize output
     * @return all violations; empty means Compile may proceed
     */
    public static List<Violation> validate(Map<String, Object> normalized) {
        List<Violation> violations = new ArrayList<>();
        Map<String, Object> flows = YamlMaps.map(normalized.get("flows"));
        Map<String, Object> business = YamlMaps.map(flows.get("business"));
        List<Object> businessSteps = YamlMaps.list(business.get("steps"));
        if (businessSteps.isEmpty()) {
            violations.add(new Violation(
                    ValidationCodes.VAL_FLOW_BUSINESS_MISSING,
                    "/flows/business/steps",
                    "business flow is missing or has no steps"
            ));
        }
        boolean hasAuthFlow = flows.containsKey("authentication")
                && !YamlMaps.list(YamlMaps.map(flows.get("authentication")).get("steps")).isEmpty();
        int maxAuthAttempts = YamlMaps.integer(YamlMaps.map(normalized.get("limits")).get("maxAuthAttempts"), 0);
        walkFlow("/flows/business", business, hasAuthFlow, maxAuthAttempts, violations);
        if (hasAuthFlow) {
            walkFlow("/flows/authentication", YamlMaps.map(flows.get("authentication")), true, maxAuthAttempts, violations);
        }
        PipelineGraphValidator.validate(normalized, violations);
        return List.copyOf(violations);
    }

    private static void walkFlow(
            String flowPath,
            Map<String, Object> flow,
            boolean hasAuthFlow,
            int maxAuthAttempts,
            List<Violation> violations
    ) {
        List<Object> steps = YamlMaps.list(flow.get("steps"));
        for (int i = 0; i < steps.size(); i++) {
            String stepPath = flowPath + "/steps/" + i;
            Map<String, Object> step = YamlMaps.map(steps.get(i));
            String stepId = YamlMaps.stringOrNull(step.get("id"));
            if (stepId == null || stepId.isBlank()) {
                violations.add(new Violation(ValidationCodes.VAL_STEP_ID_MISSING, stepPath + "/id", "step id is required"));
            }
            walkAssign(stepPath, step, violations);
            walkExtract(stepPath, step, violations);
            List<Object> transitions = YamlMaps.list(step.get("transitions"));
            for (int t = 0; t < transitions.size(); t++) {
                walkTransition(
                        stepPath + "/transitions/" + t,
                        YamlMaps.map(transitions.get(t)),
                        hasAuthFlow,
                        maxAuthAttempts,
                        violations
                );
            }
        }
    }

    private static void walkAssign(String stepPath, Map<String, Object> step, List<Violation> violations) {
        Object assign = step.get("assign");
        if (!(assign instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> assigns = YamlMaps.map(assign);
        for (String target : assigns.keySet()) {
            if (isGlobalTarget(target)) {
                violations.add(new Violation(
                        ValidationCodes.VAL_GLOBAL_WRITE,
                        stepPath + "/assign/" + target,
                        "GLOBAL is read-only at runtime"
                ));
            }
            Object raw = assigns.get(target);
            if (raw instanceof Map<?, ?>) {
                Map<String, Object> expr = YamlMaps.map(raw);
                if (expr.containsKey("now")) {
                    String now = String.valueOf(expr.get("now"));
                    if (!"epochMillis".equals(now) && !"isoOffset".equals(now)) {
                        violations.add(new Violation(
                                ValidationCodes.VAL_ASSIGN_FORM,
                                stepPath + "/assign/" + target + "/now",
                                "now must be epochMillis or isoOffset"
                        ));
                    }
                }
                if (expr.containsKey("generate")) {
                    String generate = String.valueOf(expr.get("generate"));
                    if (!"nonce".equals(generate)) {
                        violations.add(new Violation(
                                ValidationCodes.VAL_ASSIGN_FORM,
                                stepPath + "/assign/" + target + "/generate",
                                "generate must be nonce"
                        ));
                    }
                }
            }
        }
    }

    private static void walkExtract(String stepPath, Map<String, Object> step, List<Violation> violations) {
        Object extract = step.get("extract");
        if (!(extract instanceof Map<?, ?>)) {
            return;
        }
        String to = YamlMaps.stringOrNull(YamlMaps.map(extract).get("to"));
        if (to != null && isGlobalTarget(to)) {
            violations.add(new Violation(
                    ValidationCodes.VAL_GLOBAL_WRITE,
                    stepPath + "/extract/to",
                    "GLOBAL is read-only at runtime"
            ));
        }
    }

    private static boolean isGlobalTarget(String target) {
        int dot = target.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        return "GLOBAL".equals(target.substring(0, dot).toUpperCase(Locale.ROOT));
    }

    private static void walkTransition(
            String path,
            Map<String, Object> transition,
            boolean hasAuthFlow,
            int maxAuthAttempts,
            List<Violation> violations
    ) {
        Object when = transition.get("when");
        if (when == null) {
            violations.add(new Violation(ValidationCodes.VAL_WHEN_MISSING, path + "/when", "transition requires when"));
        } else {
            walkCondition(path + "/when", when, 1, violations);
        }
        String action = YamlMaps.stringOrNull(transition.get("action"));
        boolean authAction = "AUTHENTICATE".equals(action) || "REFRESH_SESSION".equals(action);
        if (transition.get("then") != null && !authAction) {
            violations.add(new Violation(
                    ValidationCodes.VAL_THEN_NOT_ALLOWED,
                    path + "/then",
                    "then is only allowed on AUTHENTICATE/REFRESH_SESSION"
            ));
        }
        if ("AUTHENTICATE".equals(action)) {
            if (transition.get("then") == null) {
                violations.add(new Violation(
                        ValidationCodes.VAL_THEN_MISSING,
                        path + "/then",
                        "AUTHENTICATE requires then"
                ));
            }
            if (!hasAuthFlow) {
                violations.add(new Violation(
                        ValidationCodes.VAL_AUTH_FLOW_REQUIRED,
                        path + "/action",
                        "AUTHENTICATE requires a non-empty authentication flow"
                ));
            }
            if (maxAuthAttempts <= 0) {
                violations.add(new Violation(
                        ValidationCodes.VAL_AUTH_ON_EMPTY,
                        path + "/action",
                        "AUTHENTICATE requires maxAuthAttempts > 0"
                ));
            }
        }
    }

    private static void walkCondition(String path, Object when, int depth, List<Violation> violations) {
        if (depth > 8) {
            violations.add(new Violation(ValidationCodes.VAL_CONDITION_DEPTH, path, "condition nesting exceeds 8"));
            return;
        }
        Map<String, Object> map = YamlMaps.map(when);
        if (map.containsKey("all")) {
            List<Object> children = YamlMaps.list(map.get("all"));
            if (children.isEmpty()) {
                violations.add(new Violation(ValidationCodes.VAL_EMPTY_ALL, path + "/all", "all must not be empty"));
                return;
            }
            for (int i = 0; i < children.size(); i++) {
                walkCondition(path + "/all/" + i, children.get(i), depth + 1, violations);
            }
            return;
        }
        if (map.containsKey("any")) {
            List<Object> children = YamlMaps.list(map.get("any"));
            if (children.isEmpty()) {
                violations.add(new Violation(ValidationCodes.VAL_EMPTY_ANY, path + "/any", "any must not be empty"));
                return;
            }
            for (int i = 0; i < children.size(); i++) {
                walkCondition(path + "/any/" + i, children.get(i), depth + 1, violations);
            }
            return;
        }
        if (map.containsKey("not")) {
            Object not = map.get("not");
            if (not instanceof List<?> list && list.size() != 1) {
                violations.add(new Violation(ValidationCodes.VAL_NOT_ARITY, path + "/not", "not requires exactly one child"));
                return;
            }
            if (not == null) {
                violations.add(new Violation(ValidationCodes.VAL_NOT_ARITY, path + "/not", "not requires exactly one child"));
                return;
            }
            walkCondition(path + "/not", not, depth + 1, violations);
            return;
        }
        if (map.containsKey("jsonpath")) {
            Map<String, Object> jsonpath = YamlMaps.map(map.get("jsonpath"));
            String jsonPath = YamlMaps.stringOrNull(jsonpath.get("path"));
            if (jsonPath != null) {
                JsonPathConditionSupport.forbiddenCode(jsonPath).ifPresent(code ->
                        violations.add(new Violation(
                                code,
                                path + "/jsonpath/path",
                                "JSONPath profile forbids this expression"
                        ))
                );
            }
        }
        if (map.containsKey("status") && map.get("status") instanceof Map<?, ?>) {
            Map<String, Object> range = YamlMaps.map(map.get("status"));
            int from = YamlMaps.integer(range.get("from"), 0);
            int to = YamlMaps.integer(range.get("to"), 0);
            if (from > to) {
                violations.add(new Violation(ValidationCodes.VAL_STATUS_RANGE, path + "/status", "status from > to"));
            }
        }
    }
}
