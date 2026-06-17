/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.mapping.exception.MappingExceptions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Strict type coercion for declarative {@code coerce} rules (D-06).
 */
public final class CoerceHelper {

    private CoerceHelper() {
    }

    public static Object coerce(Object value, String type, String path) {
        if (type == null || type.isBlank()) {
            throw MappingExceptions.coerceFailed(path, type, value);
        }
        if (value == null) {
            return null;
        }
        return switch (type) {
            case "string" -> String.valueOf(value);
            case "number" -> coerceNumber(value, path);
            case "boolean" -> coerceBoolean(value, path);
            case "date" -> coerceDate(value, path);
            default -> throw MappingExceptions.coerceFailed(path, type, value);
        };
    }

    private static Object coerceNumber(Object value, String path) {
        if (value instanceof Number number) {
            if (value instanceof BigDecimal decimal) {
                return decimal.scale() <= 0 ? decimal.longValue() : decimal;
            }
            if (value instanceof Double || value instanceof Float) {
                double d = number.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return (long) d;
                }
                return BigDecimal.valueOf(d);
            }
            return number.longValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                throw MappingExceptions.coerceFailed(path, "number", value);
            }
            try {
                if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
                    return new BigDecimal(trimmed);
                }
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                throw MappingExceptions.coerceFailed(path, "number", value);
            }
        }
        throw MappingExceptions.coerceFailed(path, "number", value);
    }

    private static Boolean coerceBoolean(Object value, String path) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String lower = text.trim().toLowerCase();
            if ("true".equals(lower) || "1".equals(lower)) {
                return true;
            }
            if ("false".equals(lower) || "0".equals(lower)) {
                return false;
            }
        }
        if (value instanceof Number number) {
            int intValue = number.intValue();
            if (intValue == 0) {
                return false;
            }
            if (intValue == 1) {
                return true;
            }
        }
        throw MappingExceptions.coerceFailed(path, "boolean", value);
    }

    private static Object coerceDate(Object value, String path) {
        if (value instanceof Instant || value instanceof LocalDate) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw MappingExceptions.coerceFailed(path, "date", value);
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException ex) {
                throw MappingExceptions.coerceFailed(path, "date", value);
            }
        }
    }
}
