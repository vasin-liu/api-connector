/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sorts query parameters by key and joins {@code key=value} pairs. Used before HMAC.
 *
 * @author Gensokyo
 * @since 2026-09-20
 */
public final class SortedQueryCanonicalizer {

    private SortedQueryCanonicalizer() {
    }

    /**
     * @param params    query name → value (insertion order is ignored)
     * @param exclude   keys omitted from the canonical string (e.g. {@code sig})
     * @param separator pair separator; {@code null} or blank becomes {@code &}
     * @return canonical string
     */
    public static String canonicalize(Map<String, String> params, Collection<String> exclude, String separator) {
        Objects.requireNonNull(params, "params");
        Set<String> skip = exclude == null ? Set.of() : Set.copyOf(exclude);
        String sep = separator == null || separator.isEmpty() ? "&" : separator;
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
}
