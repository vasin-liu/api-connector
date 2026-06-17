/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.mapping.spi.TransformStep;

import java.util.List;
import java.util.Map;

/**
 * Ordered execution of {@code ConnectorSpec.transform[]} steps by direction (D-20, D-22).
 *
 * <p><strong>Request pipeline order (D-21):</strong> {@code mapRequest → transform → auth}. The response
 * path reverses to {@code transform → mapResponse}. This class delivers the transform stage only;
 * the orchestrator wires the full ordering in Phase 3 (MAP-06 out of scope here). Keeping the
 * transform stage as a distinct bean with explicit {@link #applyRequest} / {@link #applyResponse}
 * signatures prevents the orchestrator from silently reordering crypto relative to auth signing
 * (Pitfall 1).</p>
 */
public final class TransformPipeline {

    private static final String DIRECTION_REQUEST = "request";
    private static final String DIRECTION_RESPONSE = "response";

    private final TransformStepRegistry registry;

    public TransformPipeline(TransformStepRegistry registry) {
        this.registry = registry;
    }

    /**
     * Applies request-direction transform steps in declared order (default direction is {@code request}).
     *
     * @param body           request body
     * @param transformSteps {@code ConnectorSpec.transform()} list
     * @param credentials    connector credentials for {@code keyRef} resolution
     * @return transformed body
     */
    public String applyRequest(
            String body, List<Map<String, Object>> transformSteps, Map<String, String> credentials) {
        return apply(body, transformSteps, credentials, DIRECTION_REQUEST);
    }

    /**
     * Applies response-direction transform steps in declared order.
     *
     * @param body           response body
     * @param transformSteps {@code ConnectorSpec.transform()} list
     * @param credentials    connector credentials for {@code keyRef} resolution
     * @return transformed body
     */
    public String applyResponse(
            String body, List<Map<String, Object>> transformSteps, Map<String, String> credentials) {
        return apply(body, transformSteps, credentials, DIRECTION_RESPONSE);
    }

    private String apply(
            String body,
            List<Map<String, Object>> transformSteps,
            Map<String, String> credentials,
            String direction) {
        if (transformSteps == null || transformSteps.isEmpty()) {
            return body;
        }
        String current = body;
        for (Map<String, Object> stepConfig : transformSteps) {
            if (stepConfig == null) {
                continue;
            }
            if (!matchesDirection(stepConfig, direction)) {
                continue;
            }
            String type = String.valueOf(stepConfig.get("type"));
            TransformStep step = registry.require(type);
            TransformContext ctx = new TransformContext(current, stepConfig, credentials, direction);
            current = step.apply(ctx);
        }
        return current;
    }

    private static boolean matchesDirection(Map<String, Object> stepConfig, String direction) {
        Object configured = stepConfig.get("direction");
        String stepDirection = configured == null ? DIRECTION_REQUEST : String.valueOf(configured).trim();
        if (stepDirection.isBlank()) {
            stepDirection = DIRECTION_REQUEST;
        }
        return direction.equalsIgnoreCase(stepDirection);
    }
}
