/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.jsonpath.RestrictedJsonPath;
import com.suntek.apiconnector.core.value.DataValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Evaluates Condition AST against the last transport response and committed variables.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /**
     * @param condition AST node
     * @param context   last response plus variables
     * @return true when the condition matches
     */
    public static boolean matches(Condition condition, ConditionContext context) {
        return switch (condition) {
            case Condition.StatusCondition status -> matchStatus(status, context);
            case Condition.HeaderCondition header -> matchHeader(header, context);
            case Condition.JsonPathCondition jsonPath -> matchJsonPath(jsonPath, context);
            case Condition.VariableCondition variable -> matchVariable(variable, context);
            case Condition.AllCondition all -> {
                for (Condition child : all.children()) {
                    if (!matches(child, context)) {
                        yield false;
                    }
                }
                yield true;
            }
            case Condition.AnyCondition any -> {
                for (Condition child : any.children()) {
                    if (matches(child, context)) {
                        yield true;
                    }
                }
                yield false;
            }
            case Condition.NotCondition not -> !matches(not.child(), context);
        };
    }

    private static boolean matchStatus(Condition.StatusCondition status, ConditionContext context) {
        if (context.httpStatus().isEmpty()) {
            return false;
        }
        int code = context.httpStatus().getAsInt();
        if (status.exact().isPresent()) {
            return code == status.exact().getAsInt();
        }
        return code >= status.from().getAsInt() && code <= status.to().getAsInt();
    }

    private static boolean matchHeader(Condition.HeaderCondition header, ConditionContext context) {
        if (context.httpStatus().isEmpty()) {
            return false;
        }
        List<String> values = headerValues(context.headers(), header.name());
        if (header.exists()) {
            return values.stream().anyMatch(v -> v != null && !v.isEmpty());
        }
        String expected = header.equalsValue().orElseThrow();
        return values.stream().anyMatch(expected::equals);
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? List.of() : entry.getValue();
            }
        }
        return List.of();
    }

    private static boolean matchJsonPath(Condition.JsonPathCondition jsonPath, ConditionContext context) {
        if (context.httpStatus().isEmpty()) {
            return false;
        }
        ResponseBody body = context.body().orElse(null);
        if (!(body instanceof ResponseBody.BytesBody bytesBody)) {
            return false;
        }
        String json = new String(bytesBody.bytes(), StandardCharsets.UTF_8);
        RestrictedJsonPath.JsonSelect selected = RestrictedJsonPath.select(json, jsonPath.path());
        if (!(selected instanceof RestrictedJsonPath.JsonSelect.Found found)) {
            return false;
        }
        if (jsonPath.exists()) {
            return found.value() != null;
        }
        return ScalarEquals.jsonEquals(found.value(), jsonPath.equalsValue().orElseThrow());
    }

    private static boolean matchVariable(Condition.VariableCondition variable, ConditionContext context) {
        var found = context.variables().get(variable.scope(), variable.name());
        if (found.isEmpty() || found.get() instanceof DataValue.NullValue) {
            return false;
        }
        if (variable.exists()) {
            return true;
        }
        return ScalarEquals.dataValueEquals(found.get(), variable.equalsValue().orElseThrow());
    }
}
