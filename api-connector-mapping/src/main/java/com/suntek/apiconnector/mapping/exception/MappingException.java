/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.exception;

import java.util.Map;

/**
 * Typed mapping failure with platform error code and ops-friendly details (D-14).
 */
public final class MappingException extends RuntimeException {

    private final MappingErrorCode code;
    private final Map<String, Object> details;

    public MappingException(String message, MappingErrorCode code, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public MappingException(String message, MappingErrorCode code) {
        this(message, code, Map.of());
    }

    public MappingErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
