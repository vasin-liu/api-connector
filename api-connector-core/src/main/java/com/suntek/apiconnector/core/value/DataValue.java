/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.value;

import java.util.List;
import java.util.Map;

/**
 * Typed runtime value. Secrets are a distinct subtype with no generic reveal.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public sealed interface DataValue
        permits DataValue.StringValue,
        DataValue.NumberValue,
        DataValue.BooleanValue,
        DataValue.BytesValue,
        DataValue.JsonValue,
        DataValue.ObjectValue,
        DataValue.ListValue,
        SecretValue,
        DataValue.NullValue {

    record StringValue(String value) implements DataValue {}

    record NumberValue(Number value) implements DataValue {}

    record BooleanValue(boolean value) implements DataValue {}

    record BytesValue(byte[] value) implements DataValue {}

    record JsonValue(String json) implements DataValue {}

    record ObjectValue(Map<String, DataValue> fields) implements DataValue {}

    record ListValue(List<DataValue> items) implements DataValue {}

    record NullValue() implements DataValue {}
}
