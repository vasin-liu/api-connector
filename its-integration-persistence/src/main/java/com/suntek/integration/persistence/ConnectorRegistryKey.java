/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.persistence;

/**
 * 连接器注册表业务键（code3rd + scope）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ConnectorRegistryKey {

    private ConnectorRegistryKey() {
    }

    /**
     * 规范化 scope。
     *
     * @param scope 区域
     * @return 非空 scope 键
     */
    public static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "*";
        }
        return scope.trim();
    }

    /**
     * 组合注册键。
     *
     * @param code3rd 连接器编码
     * @param scope   区域
     * @return code3rd::scope
     */
    public static String of(String code3rd, String scope) {
        return code3rd + "::" + normalizeScope(scope);
    }
}
