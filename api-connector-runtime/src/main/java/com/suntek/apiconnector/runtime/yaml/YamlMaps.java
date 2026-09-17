/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe casts for SnakeYAML maps and lists.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class YamlMaps {

    private YamlMaps() {
    }

    /**
     * @param value YAML node
     * @return map, empty if null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("expected mapping, got " + value.getClass().getSimpleName());
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    /**
     * @param value YAML node
     * @return list, empty if null
     */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> raw)) {
            throw new IllegalArgumentException("expected sequence, got " + value.getClass().getSimpleName());
        }
        return new ArrayList<>(raw);
    }

    /**
     * @param value YAML scalar
     * @return string or null
     */
    public static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * @param value YAML scalar
     * @param fallback default
     * @return int
     */
    public static int integer(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * @param value YAML scalar
     * @param fallback default
     * @return boolean
     */
    public static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
