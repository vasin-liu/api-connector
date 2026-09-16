/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.observe;

import com.suntek.apiconnector.core.observe.DecisionRecord;
import com.suntek.apiconnector.core.observe.DecisionTrace;
import com.suntek.apiconnector.runtime.secret.Redactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a redacted decision trace for one execution.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class TraceBuilder {

    private final List<DecisionRecord> decisions = new ArrayList<>();
    private final AtomicInteger seq = new AtomicInteger();
    private final AtomicInteger requestAttempts = new AtomicInteger();
    private final String executionId;

    /**
     * @param executionId host execution id
     */
    public TraceBuilder(String executionId) {
        this.executionId = executionId == null ? "" : executionId;
    }

    /**
     * @return next request-attempt id
     */
    public int nextRequestAttempt() {
        return requestAttempts.incrementAndGet();
    }

    /**
     * @param type   decision type
     * @param action selected action
     * @param reason reason code
     * @param facts  already-safe facts; Authorization values are masked
     */
    public void add(String type, String action, String reason, Map<String, String> facts) {
        Map<String, String> safe = new LinkedHashMap<>();
        safe.put("executionId", executionId);
        if (facts != null) {
            facts.forEach((key, value) -> {
                if (key != null && key.toLowerCase().contains("authorization")) {
                    safe.put(key, Redactor.MASK);
                } else {
                    safe.put(key, value == null ? "" : value);
                }
            });
        }
        decisions.add(new DecisionRecord(
                "d" + seq.incrementAndGet(),
                type,
                action,
                reason,
                Map.copyOf(safe)
        ));
    }

    /**
     * @return immutable snapshot
     */
    public DecisionTrace snapshot() {
        return new DecisionTrace(List.copyOf(decisions));
    }
}
