/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

import com.suntek.apiconnector.connectors.BuiltinConnectorCatalogs;
import com.suntek.apiconnector.api.dto.ProxyInvokeRequest;

/**
 * Catalog 受管连接器禁止自由 method+path 调用。
 */
public final class StrictEndpointsEnforcer {

    private StrictEndpointsEnforcer() {
    }

    public static void requireEndpointIdForManaged(String code3rd, ProxyInvokeRequest request) {
        if (!BuiltinConnectorCatalogs.isManaged(code3rd)) {
            return;
        }
        if (request.getEndpointId() == null || request.getEndpointId().isBlank()) {
            throw new IllegalArgumentException(
                    "Catalog-managed connector " + code3rd
                            + " requires endpointId; free method+path invoke is disabled (strictEndpoints)");
        }
    }

    public static void requireEndpointIdForManaged(String code3rd, String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("endpointId is required");
        }
        if (!BuiltinConnectorCatalogs.isManaged(code3rd)) {
            return;
        }
        // invokeEndpoint path always has endpointId — no-op beyond blank check
    }
}
