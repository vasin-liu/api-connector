/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.flow.condition.Condition;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * YAML {@code when} → Condition AST.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class ConditionYamlParser {

    private ConditionYamlParser() {
    }

    /**
     * @param when YAML when node
     * @return AST
     */
    public static Condition parse(Object when) {
        if (when == null) {
            throw new IllegalArgumentException("VAL_WHEN_MISSING");
        }
        Map<String, Object> map = YamlMaps.map(when);
        if (map.containsKey("all")) {
            return new Condition.AllCondition(children(map.get("all")));
        }
        if (map.containsKey("any")) {
            return new Condition.AnyCondition(children(map.get("any")));
        }
        if (map.containsKey("not")) {
            return Condition.NotCondition.of(parse(map.get("not")));
        }
        if (map.containsKey("status")) {
            return status(map.get("status"));
        }
        if (map.containsKey("header")) {
            return header(YamlMaps.map(map.get("header")));
        }
        if (map.containsKey("jsonpath")) {
            return jsonPath(YamlMaps.map(map.get("jsonpath")));
        }
        if (map.containsKey("variable")) {
            return variable(YamlMaps.map(map.get("variable")));
        }
        throw new IllegalArgumentException("unsupported condition: " + map.keySet());
    }

    private static List<Condition> children(Object raw) {
        List<Condition> out = new ArrayList<>();
        for (Object child : YamlMaps.list(raw)) {
            out.add(parse(child));
        }
        return out;
    }

    private static Condition status(Object raw) {
        if (raw instanceof Map<?, ?>) {
            Map<String, Object> range = YamlMaps.map(raw);
            return Condition.StatusCondition.range(
                    YamlMaps.integer(range.get("from"), 0),
                    YamlMaps.integer(range.get("to"), 0)
            );
        }
        return Condition.StatusCondition.exact(YamlMaps.integer(raw, -1));
    }

    private static Condition header(Map<String, Object> header) {
        String name = YamlMaps.stringOrNull(header.get("name"));
        if (header.get("equals") != null) {
            return Condition.HeaderCondition.equalsValue(name, String.valueOf(header.get("equals")));
        }
        return Condition.HeaderCondition.exists(name);
    }

    private static Condition jsonPath(Map<String, Object> jsonpath) {
        String path = YamlMaps.stringOrNull(jsonpath.get("path"));
        if (jsonpath.get("equals") != null) {
            return Condition.JsonPathCondition.equalsValue(path, scalar(jsonpath.get("equals")));
        }
        return Condition.JsonPathCondition.exists(path);
    }

    private static Condition variable(Map<String, Object> variable) {
        VariableScope scope = VariableScope.valueOf(String.valueOf(variable.get("scope")).toUpperCase(Locale.ROOT));
        String name = YamlMaps.stringOrNull(variable.get("name"));
        if (variable.get("equals") != null) {
            return Condition.VariableCondition.equalsValue(scope, name, scalar(variable.get("equals")));
        }
        return Condition.VariableCondition.exists(scope, name);
    }

    private static DataValue scalar(Object raw) {
        if (raw == null) {
            return new DataValue.NullValue();
        }
        if (raw instanceof Boolean b) {
            return new DataValue.BooleanValue(b);
        }
        if (raw instanceof Number n) {
            return new DataValue.NumberValue(n);
        }
        return new DataValue.StringValue(String.valueOf(raw));
    }
}
