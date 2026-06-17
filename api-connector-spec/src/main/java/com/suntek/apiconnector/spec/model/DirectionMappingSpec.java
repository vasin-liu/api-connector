/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

import java.util.List;

/**
 * Per-direction mapping: declarative rules OR Groovy script (mutually exclusive).
 */
public record DirectionMappingSpec(
        List<MappingRule> rules,
        String script) {
}
