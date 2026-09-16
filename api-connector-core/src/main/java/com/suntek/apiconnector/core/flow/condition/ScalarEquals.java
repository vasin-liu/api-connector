/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretValue;

import java.math.BigDecimal;

/**
 * Scalar equality for condition {@code equals} (string / number / boolean / null only).
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
final class ScalarEquals {

    private ScalarEquals() {
    }

    static boolean isScalar(DataValue value) {
        return value instanceof DataValue.StringValue
                || value instanceof DataValue.NumberValue
                || value instanceof DataValue.BooleanValue
                || value instanceof DataValue.NullValue;
    }

    static boolean dataValueEquals(DataValue left, DataValue right) {
        if (left instanceof SecretValue || right instanceof SecretValue) {
            return false;
        }
        if (!isScalar(left) || !isScalar(right)) {
            return false;
        }
        if (left instanceof DataValue.NullValue && right instanceof DataValue.NullValue) {
            return true;
        }
        if (left instanceof DataValue.StringValue l && right instanceof DataValue.StringValue r) {
            return l.value().equals(r.value());
        }
        if (left instanceof DataValue.BooleanValue l && right instanceof DataValue.BooleanValue r) {
            return l.value() == r.value();
        }
        if (left instanceof DataValue.NumberValue l && right instanceof DataValue.NumberValue r) {
            return numbersEqual(l.value(), r.value());
        }
        return false;
    }

    static boolean jsonEquals(Object json, DataValue expected) {
        if (expected instanceof SecretValue) {
            return false;
        }
        if (json instanceof java.util.Map || json instanceof java.util.List) {
            return false;
        }
        return switch (expected) {
            case DataValue.StringValue(String value) -> value.equals(json);
            case DataValue.BooleanValue(boolean value) -> json instanceof Boolean b && b == value;
            case DataValue.NullValue() -> json == null;
            case DataValue.NumberValue(Number value) -> json instanceof Number n && numbersEqual(value, n);
            default -> false;
        };
    }

    static boolean numbersEqual(Number a, Number b) {
        if (integral(a) && integral(b)) {
            return a.longValue() == b.longValue();
        }
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
    }

    private static boolean integral(Number n) {
        if (n instanceof Double || n instanceof Float || n instanceof BigDecimal) {
            BigDecimal d = n instanceof BigDecimal bd ? bd : new BigDecimal(n.toString());
            return d.stripTrailingZeros().scale() <= 0;
        }
        return true;
    }
}
