/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-execution cancel flag. Checked before each outbound send.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class ExecutionCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Marks the execution cancelled.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * @return true when the host has cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
