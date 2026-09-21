/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sorts query parameters and joins {@code key=value} pairs. Used before HMAC.
 *
 * @author Gensokyo
 * @since 2026-09-20
 */
public final class SortedQueryCanonicalizer {

    /**
     * How keys and values are written into the canonical string.
     */
    public enum Encoding {
        /** Sort keys, then join unescaped {@code key=value}. */
        NONE,
        /** Percent-encode each pair (RFC3986), then sort the encoded items. */
        RFC3986
    }

    private SortedQueryCanonicalizer() {
    }

    /**
     * @param params    query name → value (insertion order is ignored)
     * @param exclude   keys omitted from the canonical string (e.g. {@code sig})
     * @param separator pair separator; {@code null} or blank becomes {@code &}
     * @return canonical string using {@link Encoding#NONE}
     */
    public static String canonicalize(Map<String, String> params, Collection<String> exclude, String separator) {
        return canonicalize(params, exclude, separator, Encoding.NONE);
    }

    /**
     * @param params    query name → value (insertion order is ignored)
     * @param exclude   keys omitted from the canonical string (e.g. {@code sig})
     * @param separator pair separator; {@code null} or blank becomes {@code &}
     * @param encoding  {@link Encoding#NONE} or {@link Encoding#RFC3986}
     * @return canonical string
     */
    public static String canonicalize(
            Map<String, String> params,
            Collection<String> exclude,
            String separator,
            Encoding encoding
    ) {
        Objects.requireNonNull(params, "params");
        Encoding mode = encoding == null ? Encoding.NONE : encoding;
        Set<String> skip = exclude == null ? Set.of() : Set.copyOf(exclude);
        String sep = separator == null || separator.isEmpty() ? "&" : separator;
        if (mode == Encoding.RFC3986) {
            List<String> items = new ArrayList<>();
            params.forEach((key, value) -> {
                if (key != null && !key.isBlank() && !skip.contains(key)) {
                    items.add(uriEncode(key) + "=" + uriEncode(value == null ? "" : value));
                }
            });
            Collections.sort(items);
            return String.join(sep, items);
        }
        Map<String, String> sorted = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key != null && !key.isBlank() && !skip.contains(key)) {
                sorted.put(key, value == null ? "" : value);
            }
        });
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                out.append(sep);
            }
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    /**
     * @param raw YAML {@code encoding} value; {@code null} or blank is {@link Encoding#NONE}
     * @return encoding
     * @throws IllegalArgumentException when the value is not {@code none} or {@code rfc3986}
     */
    public static Encoding parseEncoding(String raw) {
        if (raw == null || raw.isBlank()) {
            return Encoding.NONE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> Encoding.NONE;
            case "rfc3986" -> Encoding.RFC3986;
            default -> throw new IllegalArgumentException("unknown sorted-query encoding: " + raw);
        };
    }

    private static String uriEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
