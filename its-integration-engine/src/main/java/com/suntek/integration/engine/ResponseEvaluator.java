/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine;

import com.jayway.jsonpath.JsonPath;
import com.suntek.integration.spec.model.ResponseSpec;

/**
 * 按 Connector Spec 判定厂家响应并提取 dataPath / messagePath / codePath。
 */
public class ResponseEvaluator {

    /**
     * 完整评估响应体。
     *
     * @param spec    响应映射规则，可为 null
     * @param rawBody 原始响应体
     * @return 评估结果
     */
    public ResponseEvaluation evaluate(ResponseSpec spec, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            boolean success = spec == null || spec.successWhen() == null || spec.successWhen().isBlank();
            return new ResponseEvaluation(success, null, null, null);
        }
        if (spec == null) {
            return ResponseEvaluation.emptySuccess();
        }
        try {
            Object document = JsonPath.parse(rawBody).json();
            boolean success = isSuccessOnDocument(spec, document);
            String vendorCode = readAsString(document, spec.codePath());
            String vendorMessage = readAsString(document, spec.messagePath());
            Object parsedData = readValue(document, spec.dataPath());
            return new ResponseEvaluation(success, vendorCode, vendorMessage, parsedData);
        } catch (Exception ex) {
            return new ResponseEvaluation(false, null, null, null);
        }
    }

    /**
     * 判定响应是否成功；无规则时视为成功。
     *
     * @param spec    响应映射规则，可为 null
     * @param rawBody 原始响应体
     * @return 是否成功
     */
    public boolean isSuccess(ResponseSpec spec, String rawBody) {
        return evaluate(spec, rawBody).success();
    }

    private static boolean isSuccessOnDocument(ResponseSpec spec, Object document) {
        if (spec.successWhen() == null || spec.successWhen().isBlank()) {
            return true;
        }
        String rule = spec.successWhen().trim();
        if (rule.contains("==")) {
            String[] parts = rule.split("==", 2);
            String path = normalizePath(parts[0].trim());
            String expected = parts[1].trim();
            Object value = JsonPath.read(document, path);
            return expected.equals(String.valueOf(value));
        }
        Object value = JsonPath.read(document, normalizePath(rule));
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Object readValue(Object document, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return JsonPath.read(document, normalizePath(path.trim()));
    }

    private static String readAsString(Object document, String path) {
        Object value = readValue(document, path);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizePath(String path) {
        if (path.startsWith("$")) {
            return path;
        }
        return "$." + path;
    }
}
