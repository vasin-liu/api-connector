/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.connectors;

import com.suntek.integration.spec.model.ConnectorSpec;

/**
 * 将 Catalog 受管连接器与库/控制台配置合并，避免 endpoints 双维护。
 */
public final class CatalogManagedSpecMerger {

    private CatalogManagedSpecMerger() {
    }

    /**
     * 从库同步到运行时：端点与认证以 Catalog 为准，baseUrl 允许库覆盖。
     */
    public static ConnectorSpec forRuntime(ConnectorSpec stored) {
        if (stored == null || !BuiltinConnectorCatalogs.isManaged(stored.code3rd())) {
            return stored;
        }
        ConnectorSpec catalog = BuiltinConnectorCatalogs.catalogSpec(stored.code3rd());
        return withBaseUrl(catalog, resolveBaseUrl(catalog.baseUrl(), stored.baseUrl()));
    }

    /**
     * 控制台保存：仅接受 baseUrl 变更，endpoints/auth/response 强制与 Catalog 一致。
     */
    public static ConnectorSpec forAdminSave(ConnectorSpec fromRequest) {
        if (fromRequest == null || !BuiltinConnectorCatalogs.isManaged(fromRequest.code3rd())) {
            return fromRequest;
        }
        ConnectorSpec catalog = BuiltinConnectorCatalogs.catalogSpec(fromRequest.code3rd());
        return withBaseUrl(catalog, resolveBaseUrl(catalog.baseUrl(), fromRequest.baseUrl()));
    }

    public static void assertDeletable(String code3rd) {
        if (BuiltinConnectorCatalogs.isManaged(code3rd)) {
            throw new IllegalArgumentException(
                    "Connector " + code3rd + " is catalog-managed; remove from BuiltinConnectorCatalogs in code, not via console");
        }
    }

    public static void assertCreatable(String code3rd) {
        if (BuiltinConnectorCatalogs.isManaged(code3rd)) {
            throw new IllegalArgumentException(
                    "Connector " + code3rd + " is reserved by built-in catalog; choose another code3rd");
        }
    }

    private static String resolveBaseUrl(String catalogDefault, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return catalogDefault;
    }

    private static ConnectorSpec withBaseUrl(ConnectorSpec catalog, String baseUrl) {
        return new ConnectorSpec(
                catalog.code3rd(),
                catalog.version(),
                baseUrl,
                catalog.protocol(),
                catalog.auth(),
                catalog.endpoints(),
                catalog.response(),
                catalog.transport(),
                catalog.transform());
    }
}
