/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

import java.util.Set;

/**
 * Known declarative mapping transform operations (D-02).
 */
public final class MappingOps {

    public static final String RENAME = "rename";
    public static final String COERCE = "coerce";
    public static final String NEST = "nest";
    public static final String ARRAY_MAP = "array_map";
    public static final String SET = "set";

    public static final Set<String> KNOWN = Set.of(RENAME, COERCE, NEST, ARRAY_MAP, SET);

    public static final Set<String> COERCE_TYPES = Set.of("string", "number", "boolean", "date");

    private MappingOps() {
    }
}
