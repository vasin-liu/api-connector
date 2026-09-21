/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.secret.CredentialResolver;
import com.suntek.apiconnector.runtime.session.SessionCoordinator;
import com.suntek.apiconnector.runtime.time.NonceSource;
import com.suntek.apiconnector.transport.HttpTransport;

import java.time.Clock;
import java.util.Map;

/**
 * Facade over {@link FlowRuntime} so existing 0a call sites keep compiling.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class LinearFlowExecutor {

    private LinearFlowExecutor() {
    }

    /**
     * @param plan      compiled plan
     * @param transport outbound HTTP
     * @return terminal result
     */
    public static ExecutionResult execute(ExecutionPlan plan, HttpTransport transport) {
        return execute(plan, transport, Map.of(), new ExecutionCancellation(), new SessionCoordinator());
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION-scoped host input
     * @param cancellation cancel flag
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation
    ) {
        return execute(plan, transport, input, cancellation, new SessionCoordinator());
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION-scoped host input
     * @param cancellation cancel flag
     * @param sessions     shared session coordinator
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions
    ) {
        return execute(plan, transport, input, cancellation, sessions, Clock.systemUTC());
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION-scoped host input
     * @param cancellation cancel flag
     * @param sessions     shared session coordinator
     * @param clock        injectable clock for {@code now: epochMillis}
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock
    ) {
        return FlowRuntime.execute(plan, transport, input, cancellation, sessions, clock);
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION-scoped host input
     * @param cancellation cancel flag
     * @param sessions     shared session coordinator
     * @param clock        injectable clock
     * @param secrets      credential resolver
     * @param executionId  host execution id
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock,
            CredentialResolver secrets,
            String executionId
    ) {
        return FlowRuntime.execute(plan, transport, input, cancellation, sessions, clock, secrets, executionId);
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION-scoped host input
     * @param cancellation cancel flag
     * @param sessions     shared session coordinator
     * @param clock        injectable clock
     * @param secrets      credential resolver
     * @param executionId  host execution id
     * @param nonce        injectable nonce source
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock,
            CredentialResolver secrets,
            String executionId,
            NonceSource nonce
    ) {
        return FlowRuntime.execute(plan, transport, input, cancellation, sessions, clock, secrets, executionId, nonce);
    }
}
