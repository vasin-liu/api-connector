/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

/**
 * Execution-wide policy. ReplayPolicy may only downgrade resend to FAIL.
 *
 * @param onUnknownOutcome    FAIL unless explicitly allow replay
 * @param onStaleFlowBinding  RETRY_FLOW when a one-time binding is consumed
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecutionPolicy(String onUnknownOutcome, String onStaleFlowBinding) {

    /**
     * @return defaults
     */
    public static ExecutionPolicy defaults() {
        return new ExecutionPolicy("FAIL", "FAIL");
    }
}
