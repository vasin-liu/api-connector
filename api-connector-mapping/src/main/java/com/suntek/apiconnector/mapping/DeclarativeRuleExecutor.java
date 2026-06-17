/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.spec.model.MappingOps;
import com.suntek.apiconnector.spec.model.MappingRule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sequential declarative mapping rule executor over Jayway {@link DocumentContext} (D-02, D-07).
 */
public final class DeclarativeRuleExecutor {

    /**
     * Applies mapping rules in list order; later rules override the same target path (D-07).
     *
     * @param inputJson source JSON document
     * @param rules     ordered declarative rules
     * @return transformed JSON string
     */
    public String applyRules(String inputJson, List<MappingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return normalizeInput(inputJson);
        }
        DocumentContext ctx = JsonPath.parse(normalizeInput(inputJson));
        for (MappingRule rule : rules) {
            applyRule(ctx, rule);
        }
        return ctx.jsonString();
    }

    private static String normalizeInput(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return "{}";
        }
        return inputJson;
    }

    private static void applyRule(DocumentContext ctx, MappingRule rule) {
        String op = rule.op();
        if (op == null || op.isBlank()) {
            throw new IllegalArgumentException("Mapping rule op is required");
        }
        switch (op) {
            case MappingOps.RENAME -> applyRename(ctx, rule);
            case MappingOps.SET -> applySet(ctx, rule);
            case MappingOps.COERCE -> applyCoerce(ctx, rule);
            case MappingOps.NEST -> applyNest(ctx, rule);
            case MappingOps.ARRAY_MAP -> throw new IllegalArgumentException("array_map is not yet supported");
            default -> throw new IllegalArgumentException("Unknown mapping op: " + op);
        }
    }

    private static void applyRename(DocumentContext ctx, MappingRule rule) {
        String source = PathNormalizer.normalize(rule.source());
        String target = PathNormalizer.normalize(rule.target());
        Object value = readLenient(ctx, source);
        if (value == null) {
            return;
        }
        writeValue(ctx, target, value);
        deleteLenient(ctx, source);
    }

    private static void applySet(DocumentContext ctx, MappingRule rule) {
        String target = PathNormalizer.normalize(rule.target());
        writeValue(ctx, target, rule.value());
    }

    private static void applyCoerce(DocumentContext ctx, MappingRule rule) {
        String source = PathNormalizer.normalize(rule.source());
        String target = PathNormalizer.normalize(rule.target());
        Object value = readLenient(ctx, source);
        if (value == null) {
            return;
        }
        Object coerced = CoerceHelper.coerce(value, rule.type(), source);
        writeValue(ctx, target, coerced);
    }

    private static void applyNest(DocumentContext ctx, MappingRule rule) {
        String source = PathNormalizer.normalize(rule.source());
        String target = PathNormalizer.normalize(rule.target());
        Object value = readLenient(ctx, source);
        if (value == null) {
            return;
        }
        writeValue(ctx, target, value);
    }

    private static Object readLenient(DocumentContext ctx, String path) {
        try {
            return ctx.read(path);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void deleteLenient(DocumentContext ctx, String path) {
        try {
            ctx.delete(path);
        } catch (RuntimeException ex) {
            // missing source path — lenient no-op
        }
    }

    private static void writeValue(DocumentContext ctx, String targetPath, Object value) {
        String normalized = PathNormalizer.normalize(targetPath);
        if (!normalized.startsWith("$.")) {
            throw new IllegalArgumentException("Target path must be under root: " + targetPath);
        }
        String remainder = normalized.substring(2);
        if (remainder.isEmpty()) {
            throw new IllegalArgumentException("Target path must name a field: " + targetPath);
        }
        int lastDot = remainder.lastIndexOf('.');
        if (lastDot < 0) {
            ctx.put("$", remainder, value);
            return;
        }
        String parentPath = "$." + remainder.substring(0, lastDot);
        String leafKey = remainder.substring(lastDot + 1);
        ensureParentObjects(ctx, normalized);
        ctx.put(parentPath, leafKey, value);
    }

    private static void ensureParentObjects(DocumentContext ctx, String targetPath) {
        String normalized = PathNormalizer.normalize(targetPath);
        if (!normalized.startsWith("$.")) {
            return;
        }
        String remainder = normalized.substring(2);
        if (remainder.isEmpty()) {
            return;
        }
        String[] segments = remainder.split("\\.");
        if (segments.length <= 1) {
            return;
        }
        StringBuilder current = new StringBuilder("$");
        for (int i = 0; i < segments.length - 1; i++) {
            String parentPath = current.toString();
            String segment = segments[i];
            current.append('.').append(segment);
            String fullPath = current.toString();
            Object existing = readLenient(ctx, fullPath);
            if (!(existing instanceof Map<?, ?>)) {
                ctx.put(parentPath, segment, new LinkedHashMap<String, Object>());
            }
        }
    }
}
