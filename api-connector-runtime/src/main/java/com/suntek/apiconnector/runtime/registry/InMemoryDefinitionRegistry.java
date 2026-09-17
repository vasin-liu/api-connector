/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.registry;

import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory published-plan registry and cache keyed by definition id + revision.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class InMemoryDefinitionRegistry {

    private final ConcurrentHashMap<PlanKey, ExecutionPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PlanKey, DefinitionLifecycle> lifecycle = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlanKey> publishedHead = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PlanKey> latestById = new ConcurrentHashMap<>();

    /**
     * Loads a valid definition as {@link DefinitionLifecycle#PUBLISHED}.
     *
     * @param yaml Canonical Definition YAML
     * @return compiled plan
     */
    public ExecutionPlan load(String yaml) {
        return load(yaml, DefinitionLifecycle.PUBLISHED);
    }

    /**
     * @param yaml       Canonical Definition YAML
     * @param lifecycle  explicit lifecycle
     * @return compiled plan (cached by id+revision)
     */
    public ExecutionPlan load(String yaml, DefinitionLifecycle lifecycle) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(lifecycle, "lifecycle");
        ExecutionPlan compiled = PlanCompiler.compile(yaml);
        PlanKey key = new PlanKey(compiled.definitionId(), compiled.definitionRevision());
        ExecutionPlan cached = plans.compute(key, (k, existing) -> existing == null ? compiled : existing);
        this.lifecycle.put(key, lifecycle);
        latestById.put(cached.definitionId(), key);
        if (lifecycle == DefinitionLifecycle.PUBLISHED) {
            publishedHead.put(cached.definitionId(), key);
        }
        return cached;
    }

    /**
     * Registers an already compiled plan as published (test helper).
     *
     * @param plan compiled plan
     */
    public void registerPublished(ExecutionPlan plan) {
        PlanKey key = new PlanKey(plan.definitionId(), plan.definitionRevision());
        plans.put(key, plan);
        lifecycle.put(key, DefinitionLifecycle.PUBLISHED);
        latestById.put(plan.definitionId(), key);
        publishedHead.put(plan.definitionId(), key);
    }

    /**
     * @param apiId    definition id
     * @param revision empty means current published head
     * @return published plan
     */
    public ExecutionPlan requirePublished(String apiId, Optional<String> revision) {
        PlanKey key = revision
                .map(rev -> new PlanKey(apiId, rev))
                .orElseGet(() -> {
                    PlanKey published = publishedHead.get(apiId);
                    return published != null ? published : latestById.get(apiId);
                });
        if (key == null) {
            throw new ExecuteException(ExecuteException.DEFINITION_NOT_FOUND, "no definition for " + apiId);
        }
        ExecutionPlan plan = plans.get(key);
        if (plan == null) {
            throw new ExecuteException(ExecuteException.DEFINITION_NOT_FOUND, "no plan for " + key);
        }
        DefinitionLifecycle state = lifecycle.getOrDefault(key, DefinitionLifecycle.DRAFT);
        if (state != DefinitionLifecycle.PUBLISHED) {
            throw new ExecuteException(
                    ExecuteException.DEFINITION_NOT_PUBLISHED,
                    "definition " + key.id() + "@" + key.revision() + " is " + state
            );
        }
        return plan;
    }

    private record PlanKey(String id, String revision) {
    }
}
