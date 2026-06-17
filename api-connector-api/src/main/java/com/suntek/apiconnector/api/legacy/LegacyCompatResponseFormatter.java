/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import org.springframework.stereotype.Component;

/**
 * 将 {@link ProxyInvokeResponse} 格式化为旧客户端期望的 JSON 字符串。
 */
@Component
public class LegacyCompatResponseFormatter {

    private final ObjectMapper objectMapper;

    public LegacyCompatResponseFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String format(ProxyInvokeResponse body, LegacyResponseStyle style) {
        if (style == LegacyResponseStyle.SUNTEK_RESULT) {
            return toSuntekResultJson(body);
        }
        return body.getRawBody() != null ? body.getRawBody() : "";
    }

    private String toSuntekResultJson(ProxyInvokeResponse body) {
        LegacySuntekResult result = LegacySuntekResult.builder()
                .success(body.isSuccess())
                .code(body.isSuccess() ? "200" : defaultFailCode(body))
                .message(resolveMessage(body))
                .data(resolveData(body))
                .build();
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize legacy Result", e);
        }
    }

    private static String defaultFailCode(ProxyInvokeResponse body) {
        if (body.getVendorCode() != null && !body.getVendorCode().isBlank()) {
            return body.getVendorCode();
        }
        return body.getVendorHttpStatus() > 0 ? String.valueOf(body.getVendorHttpStatus()) : "500";
    }

    private static String resolveMessage(ProxyInvokeResponse body) {
        if (body.getVendorMessage() != null && !body.getVendorMessage().isBlank()) {
            return body.getVendorMessage();
        }
        return body.isSuccess() ? "success" : "invoke failed";
    }

    private Object resolveData(ProxyInvokeResponse body) {
        if (body.getData() != null) {
            return body.getData();
        }
        String raw = body.getRawBody();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject() && node.has("data")) {
                return objectMapper.treeToValue(node.get("data"), Object.class);
            }
            if (node.isObject() && node.has("obj")) {
                return objectMapper.treeToValue(node.get("obj"), Object.class);
            }
            return objectMapper.treeToValue(node, Object.class);
        } catch (Exception ignored) {
            return raw;
        }
    }
}
