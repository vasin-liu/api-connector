/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

/**
 * Normalizes JSONPath expressions for Jayway (bare field → {@code $.field}).
 */
public final class PathNormalizer {

    private PathNormalizer() {
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.startsWith("$")) {
            return path;
        }
        return "$." + path;
    }
}
