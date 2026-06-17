/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

/**
 * Input for error-mapping gate aligned with {@code ResponseEvaluator} business success (D-15, D-19).
 *
 * <p>Phase 3 orchestrator contract: call {@link com.suntek.apiconnector.mapping.spi.MappingEngine#mapError}
 * only when {@link #shouldMapError(ErrorMappingTrigger)} is true and {@link ResolvedMapping#hasError()}.</p>
 */
public record ErrorMappingTrigger(int httpStatus, boolean businessSuccess) {

    /**
     * True when HTTP status is non-2xx or the vendor body failed the {@code successWhen} gate.
     */
    public static boolean shouldMapError(ErrorMappingTrigger trigger) {
        if (trigger == null) {
            return false;
        }
        return trigger.httpStatus() >= 400 || !trigger.businessSuccess();
    }
}
