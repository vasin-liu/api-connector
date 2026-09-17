/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON for planId hashing. Object keys are sorted; array order is preserved.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /**
     * @param node map, list, or scalar
     * @return canonical JSON text
     */
    public static String stringify(Object node) {
        StringBuilder sb = new StringBuilder();
        write(sb, node);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object node) {
        if (node == null) {
            sb.append("null");
            return;
        }
        if (node instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            sb.append('{');
            Iterator<Map.Entry<String, Object>> it = sorted.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                writeString(sb, e.getKey());
                sb.append(':');
                write(sb, e.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
            return;
        }
        if (node instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, list.get(i));
            }
            sb.append(']');
            return;
        }
        if (node instanceof String s) {
            writeString(sb, s);
            return;
        }
        if (node instanceof Boolean || node instanceof Number) {
            sb.append(node);
            return;
        }
        writeString(sb, String.valueOf(node));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
