/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretValue;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Minimal JSON encoder for codec.json (object → bytes).
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class JsonBytes {

    private JsonBytes() {
    }

    /**
     * @param value runtime value
     * @return UTF-8 JSON
     */
    public static byte[] utf8(DataValue value) {
        return encode(value).getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(DataValue value) {
        return switch (value) {
            case null -> "null";
            case DataValue.NullValue ignored -> "null";
            case DataValue.BooleanValue(boolean b) -> Boolean.toString(b);
            case DataValue.NumberValue(Number n) -> String.valueOf(n);
            case DataValue.StringValue(String s) -> quote(s);
            case DataValue.JsonValue(String json) -> json == null || json.isBlank() ? "null" : json;
            case DataValue.ObjectValue(Map<String, DataValue> fields) -> encodeObject(fields);
            case DataValue.ListValue(java.util.List<DataValue> items) -> encodeList(items);
            case DataValue.BytesValue(byte[] bytes) -> quote(new String(bytes, StandardCharsets.UTF_8));
            case SecretValue secret -> quote(utf8Secret(secret));
        };
    }

    private static String utf8Secret(SecretValue secret) {
        String[] holder = new String[1];
        secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
        return holder[0] == null ? "" : holder[0];
    }

    private static String encodeObject(Map<String, DataValue> fields) {
        StringBuilder out = new StringBuilder("{");
        Iterator<Map.Entry<String, DataValue>> it = fields.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, DataValue> entry = it.next();
            out.append(quote(entry.getKey())).append(':').append(encode(entry.getValue()));
            if (it.hasNext()) {
                out.append(',');
            }
        }
        return out.append('}').toString();
    }

    private static String encodeList(java.util.List<DataValue> items) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(encode(items.get(i)));
        }
        return out.append(']').toString();
    }

    private static String quote(String raw) {
        String value = raw == null ? "" : raw;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
