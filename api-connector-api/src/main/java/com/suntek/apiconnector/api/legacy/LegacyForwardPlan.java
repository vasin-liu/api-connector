/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import java.util.Map;

/**
 * Legacy 请求转发计划（路径别名 / 方法改写后）。
 */
public record LegacyForwardPlan(
        String code3rd,
        String method,
        String path,
        Map<String, String> query,
        String body,
        LegacyResponseStyle responseStyle) {

    public LegacyForwardPlan(String code3rd, String method, String path, Map<String, String> query, String body) {
        this(code3rd, method, path, query, body, null);
    }
}
