/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

/**
 * In-process host API. No Spring, no {@code code3rd} proxy.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public interface ApiClient {

    /**
     * Starts an execution against a published definition.
     *
     * @param command execute command
     * @return handle bound to an immutable snapshot
     */
    ExecutionHandle execute(ExecuteCommand command);

    /**
     * Cancels an in-flight execution. Outcome is CANCELLED; no retry/replay/auth.
     *
     * @param executionId execution to cancel
     */
    void cancel(String executionId);
}
