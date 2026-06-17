/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

import java.util.List;

/**
 * Single declarative mapping rule (rename, coerce, nest, array_map, set).
 */
public record MappingRule(
        String op,
        String source,
        String target,
        String type,
        Object value,
        List<MappingRule> rules) {
}
