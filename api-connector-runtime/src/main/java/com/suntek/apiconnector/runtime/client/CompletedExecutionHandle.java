/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.client;

import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.api.ExecutionSnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handle bound to an immutable snapshot. {@link #result()} may complete asynchronously.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class CompletedExecutionHandle implements ExecutionHandle {

    private final ExecutionSnapshot snapshot;
    private final CompletionStage<ExecutionResult> result;

    /**
     * @param snapshot immutable snapshot
     * @param result   already completed result
     */
    public CompletedExecutionHandle(ExecutionSnapshot snapshot, ExecutionResult result) {
        this(snapshot, CompletableFuture.completedFuture(result));
    }

    /**
     * @param snapshot immutable snapshot
     * @param result   in-flight or completed stage
     */
    public CompletedExecutionHandle(ExecutionSnapshot snapshot, CompletionStage<ExecutionResult> result) {
        this.snapshot = snapshot;
        this.result = result;
    }

    @Override
    public String executionId() {
        return snapshot.executionId();
    }

    @Override
    public ExecutionSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public CompletionStage<ExecutionResult> result() {
        return result;
    }
}
