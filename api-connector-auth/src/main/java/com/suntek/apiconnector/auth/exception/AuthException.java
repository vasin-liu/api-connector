/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.exception;

import java.util.Map;

/**
 * Typed auth failure with platform error code and ops-friendly details (D-17, D-18).
 */
public final class AuthException extends RuntimeException {

    private final AuthErrorCode code;
    private final Map<String, Object> details;

    public AuthException(String message, AuthErrorCode code, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public AuthException(String message, AuthErrorCode code) {
        this(message, code, Map.of());
    }

    public AuthErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
