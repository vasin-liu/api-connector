/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.TransformStep;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves {@link TransformStep} beans by {@code type()} (D-20).
 *
 * <p>Wired from a Spring {@code List<TransformStep>} injection; also constructible directly for tests.</p>
 */
public final class TransformStepRegistry {

    private final Map<String, TransformStep> stepsByType;

    public TransformStepRegistry(List<TransformStep> steps) {
        Map<String, TransformStep> map = new LinkedHashMap<>();
        if (steps != null) {
            for (TransformStep step : steps) {
                if (step == null || step.type() == null || step.type().isBlank()) {
                    continue;
                }
                map.put(step.type(), step);
            }
        }
        this.stepsByType = Map.copyOf(map);
    }

    /**
     * Returns the step for {@code type}, throwing {@code TRANSFORM_UNSUPPORTED} when none registered.
     *
     * @param type transform type
     * @return registered step
     */
    public TransformStep require(String type) {
        TransformStep step = stepsByType.get(type);
        if (step == null) {
            throw MappingExceptions.transformUnsupported(type);
        }
        return step;
    }

    /**
     * @param type transform type
     * @return {@code true} when a step is registered for {@code type}
     */
    public boolean isRegistered(String type) {
        return type != null && stepsByType.containsKey(type);
    }

    /**
     * @return immutable set of registered transform types
     */
    public Set<String> registeredTypes() {
        return stepsByType.keySet();
    }

    /**
     * @return registered step beans
     */
    public Collection<TransformStep> steps() {
        return stepsByType.values();
    }
}
