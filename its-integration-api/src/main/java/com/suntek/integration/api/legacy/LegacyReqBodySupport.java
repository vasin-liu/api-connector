/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析旧 thirdpart {@code ReqBody<T>} 形态 JSON。
 */
public final class LegacyReqBodySupport {

    private LegacyReqBodySupport() {
    }

    public static Map<String, String> queryFromBody(ObjectMapper mapper, String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data != null && data.isObject()) {
                data.fields().forEachRemaining(entry -> {
                    if (!entry.getValue().isNull()) {
                        map.put(entry.getKey(), entry.getValue().asText());
                    }
                });
            }
        } catch (Exception ignored) {
            // 非 JSON 时忽略
        }
        return map;
    }

    public static Map<String, String> mergeQuery(Map<String, String> servlet, Map<String, String> fromBody) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (servlet != null) {
            merged.putAll(servlet);
        }
        if (fromBody != null) {
            merged.putAll(fromBody);
        }
        return merged;
    }
}
