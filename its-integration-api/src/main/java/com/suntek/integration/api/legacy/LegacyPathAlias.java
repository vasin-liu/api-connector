/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

/**
 * 旧业务路径 → 厂家 HTTP 路径别名。
 */
public record LegacyPathAlias(
        String pathPrefix,
        String legacyPath,
        String code3rd,
        String method,
        String vendorPath,
        boolean reqBodyToQuery) {
}
