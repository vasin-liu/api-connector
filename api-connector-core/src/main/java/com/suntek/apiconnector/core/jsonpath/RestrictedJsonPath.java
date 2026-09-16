/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.jsonpath;

/**
 * Restricted JSONPath profile for Condition AST: Root, Property, Array Index only.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class RestrictedJsonPath {

    private RestrictedJsonPath() {
    }

    /**
     * Validates that {@code path} is inside the Phase 0 JSONPath profile.
     *
     * @param path JSONPath expression
     * @throws ForbiddenJsonPathException if filters, recursive descent, or calls are present
     * @throws IllegalArgumentException if the path is blank or not rooted
     */
    public static void validate(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("JSONPath must not be blank");
        }
        if (path.contains("..") || path.contains("?(") || path.contains("(") || path.contains("@")) {
            throw new ForbiddenJsonPathException(path);
        }
        if (!path.startsWith("$")) {
            throw new IllegalArgumentException("JSONPath must start with $: " + path);
        }
        int i = 1;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                if (i >= path.length() || !isIdentStart(path.charAt(i))) {
                    throw new IllegalArgumentException("JSONPath property expected: " + path);
                }
                i++;
                while (i < path.length() && isIdentPart(path.charAt(i))) {
                    i++;
                }
            } else if (c == '[') {
                i++;
                if (i >= path.length() || !Character.isDigit(path.charAt(i))) {
                    throw new ForbiddenJsonPathException(path);
                }
                while (i < path.length() && Character.isDigit(path.charAt(i))) {
                    i++;
                }
                if (i >= path.length() || path.charAt(i) != ']') {
                    throw new IllegalArgumentException("JSONPath index not closed: " + path);
                }
                i++;
            } else {
                throw new IllegalArgumentException("JSONPath syntax error at " + i + ": " + path);
            }
        }
    }

    /**
     * Selects a value from JSON text. Invalid JSON or a missing path yields {@link JsonSelect.Missing}.
     *
     * @param json JSON document
     * @param path restricted JSONPath
     * @return missing or found (found may hold {@code null})
     */
    public static JsonSelect select(String json, String path) {
        validate(path);
        if (json == null || json.isBlank()) {
            return JsonSelect.missing();
        }
        Object current;
        try {
            current = JsonTreeParser.parse(json);
        } catch (JsonTreeParser.JsonParseException ex) {
            return JsonSelect.missing();
        }
        int i = 1;
        while (i < path.length()) {
            if (current == null) {
                return JsonSelect.missing();
            }
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                int start = i;
                i++;
                while (i < path.length() && isIdentPart(path.charAt(i))) {
                    i++;
                }
                String prop = path.substring(start, i);
                if (!(current instanceof java.util.Map<?, ?> map) || !map.containsKey(prop)) {
                    return JsonSelect.missing();
                }
                current = map.get(prop);
            } else if (c == '[') {
                i++;
                int start = i;
                while (i < path.length() && Character.isDigit(path.charAt(i))) {
                    i++;
                }
                int index = Integer.parseInt(path.substring(start, i));
                i++;
                if (!(current instanceof java.util.List<?> list) || index >= list.size()) {
                    return JsonSelect.missing();
                }
                current = list.get(index);
            } else {
                throw new IllegalArgumentException("JSONPath syntax error at " + i + ": " + path);
            }
        }
        return JsonSelect.found(current);
    }

    /**
     * Result of a restricted JSONPath select.
     */
    public sealed interface JsonSelect permits JsonSelect.Missing, JsonSelect.Found {

        /**
         * @return missing result
         */
        static JsonSelect missing() {
            return Missing.INSTANCE;
        }

        /**
         * @param value selected value, may be {@code null}
         * @return found result
         */
        static JsonSelect found(Object value) {
            return new Found(value);
        }

        /**
         * Path did not resolve.
         */
        record Missing() implements JsonSelect {
            private static final Missing INSTANCE = new Missing();
        }

        /**
         * Path resolved. {@code value} may be JSON null.
         *
         * @param value selected value
         */
        record Found(Object value) implements JsonSelect {
        }
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
