/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

/**
 * Invoke 审计事件。
 *
 * <p>{@code requestId} 为本次调用的关联 id（来源 X-Request-Id / X-Trace-Id / 服务端 UUID，D-10），
 * {@code outcome} 区分 {@code SUCCESS} / {@code VENDOR_ERROR} / {@code PIPELINE_ERROR(<stage>)}（D-14）。
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
        String context,
        String requestId,
        String outcome) {

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

    public InvokeAuditEvent(
            String code3rd,
            String endpointId,
            String method,
            String path,
            boolean success,
            int vendorHttpStatus,
            long latencyMillis,
            String clientAddress,
            String context) {
        this(code3rd, endpointId, method, path, success, vendorHttpStatus, latencyMillis, clientAddress, context,
                "-", "SUCCESS");
    }
}
