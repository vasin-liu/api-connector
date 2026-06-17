/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import java.util.Map;

/**
 * Invocation context for a single {@link com.suntek.apiconnector.mapping.spi.TransformStep} (D-22).
 *
 * @param body        body to transform (UTF-8 JSON or ciphertext)
 * @param stepConfig  this step's {@code transform[]} entry (type, keyRef, direction, mode, ...)
 * @param credentials connector credentials for {@code keyRef} resolution — never inline secrets (Pitfall 6)
 * @param direction   {@code request} or {@code response}
 */
public record TransformContext(
        String body,
        Map<String, Object> stepConfig,
        Map<String, String> credentials,
        String direction) {

    public TransformContext {
        stepConfig = stepConfig == null ? Map.of() : stepConfig;
        credentials = credentials == null ? Map.of() : credentials;
    }

    /**
     * Reads a string field from this step's config, or {@code null} when absent.
     *
     * @param key config key
     * @return config value as string, or {@code null}
     */
    public String config(String key) {
        Object value = stepConfig.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
