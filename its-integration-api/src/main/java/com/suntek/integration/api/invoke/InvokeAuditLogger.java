/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.invoke;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 结构化 invoke 审计日志（SLF4J）。
 */
@Component
public class InvokeAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("integration.invoke.audit");

    public void log(InvokeAuditEvent event) {
        AUDIT.info(
                "code3rd={} endpointId={} method={} path={} success={} vendorHttpStatus={} latencyMs={} client={} context={}",
                event.code3rd(),
                event.endpointId(),
                event.method(),
                event.path(),
                event.success(),
                event.vendorHttpStatus(),
                event.latencyMillis(),
                event.clientAddress(),
                event.context());
    }
}
