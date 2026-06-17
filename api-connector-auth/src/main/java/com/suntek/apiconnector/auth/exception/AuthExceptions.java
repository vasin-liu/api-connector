/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.exception;

import com.suntek.apiconnector.scripting.ScriptCompileException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory helpers for {@link AuthException} with consistent details shape.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static AuthException profileMissing(String profileType, String code3rd) {
        return new AuthException(
                "No AuthProvider for type: " + profileType,
                AuthErrorCode.AUTH_PROFILE_MISSING,
                Map.of("profileType", profileType, "code3rd", code3rd));
    }

    public static AuthException upstreamFailed(
            String message, String code3rd, String profileType, Integer httpStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code3rd", code3rd);
        details.put("profileType", profileType);
        if (httpStatus != null) {
            details.put("httpStatus", httpStatus);
        }
        return new AuthException(message, AuthErrorCode.UPSTREAM_AUTH_FAILED, details);
    }

    public static AuthException scriptCompileError(ScriptCompileException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("label", ex.label());
        if (ex.line() != null) {
            details.put("line", ex.line());
        }
        details.put("message", ex.getMessage());
        return new AuthException(ex.getMessage(), AuthErrorCode.AUTH_SCRIPT_COMPILE_ERROR, details);
    }

    public static AuthException scriptRuntimeError(String message, String code3rd) {
        return new AuthException(
                message,
                AuthErrorCode.AUTH_SCRIPT_RUNTIME_ERROR,
                Map.of("code3rd", code3rd, "message", message));
    }
}
