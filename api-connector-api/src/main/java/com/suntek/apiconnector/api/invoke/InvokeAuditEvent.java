/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

/**
 * Invoke 审计事件。
 */
public record InvokeAuditEvent(
        String code3rd,
        String endpointId,
        String method,
        String path,
        boolean success,
        int vendorHttpStatus,
        long latencyMillis,
        String clientAddress,
        String context) {

    public InvokeAuditEvent(
            String code3rd,
            String endpointId,
            String method,
            String path,
            boolean success,
            int vendorHttpStatus,
            long latencyMillis,
            String clientAddress) {
        this(code3rd, endpointId, method, path, success, vendorHttpStatus, latencyMillis, clientAddress, "RUNTIME");
    }
}
