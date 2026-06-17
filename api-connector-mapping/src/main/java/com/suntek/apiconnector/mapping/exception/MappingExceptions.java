/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.exception;

import com.suntek.apiconnector.scripting.ScriptCompileException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory helpers for {@link MappingException} with consistent details shape.
 */
public final class MappingExceptions {

    private MappingExceptions() {
    }

    public static MappingException coerceFailed(String path, String expectedType, Object actual) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", path);
        details.put("expectedType", expectedType);
        if (actual != null) {
            details.put("actual", String.valueOf(actual));
        }
        return new MappingException(
                "Coercion to " + expectedType + " failed at " + path,
                MappingErrorCode.MAPPING_COERCE_FAILED,
                details);
    }

    public static MappingException specInvalid(String field, String path, Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", field);
        details.put("path", path);
        if (cause != null && cause.getMessage() != null) {
            details.put("message", cause.getMessage());
        }
        String message = "Invalid mapping spec at " + field + ": " + path;
        if (cause != null && cause.getMessage() != null) {
            message = message + " — " + cause.getMessage();
        }
        MappingException ex = new MappingException(message, MappingErrorCode.MAPPING_SPEC_INVALID, details);
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }

    public static MappingException scriptCompileError(ScriptCompileException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("label", ex.label());
        if (ex.line() != null) {
            details.put("line", ex.line());
        }
        details.put("message", ex.getMessage());
        return new MappingException(ex.getMessage(), MappingErrorCode.MAPPING_SCRIPT_COMPILE_ERROR, details);
    }

    public static MappingException scriptRuntimeError(String message, String code3rd) {
        return new MappingException(
                message,
                MappingErrorCode.MAPPING_SCRIPT_RUNTIME_ERROR,
                Map.of("code3rd", code3rd, "message", message));
    }
}
