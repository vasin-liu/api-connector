/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.state;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.runtime.plan.CompiledVariable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory VariableRuntime. GLOBAL is seeded from the plan and is read-only.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class DefaultVariableRuntime implements VariableRuntime {

    private final EnumMap<VariableScope, Map<String, DataValue>> committed = new EnumMap<>(VariableScope.class);

    /**
     * @param variables compiled declarations
     */
    public DefaultVariableRuntime(Map<String, CompiledVariable> variables) {
        for (VariableScope scope : VariableScope.values()) {
            committed.put(scope, new HashMap<>());
        }
        variables.values().forEach(variable ->
                variable.initial().ifPresent(value ->
                        committed.get(variable.scope()).put(variable.name(), value)
                )
        );
    }

    @Override
    public Optional<DataValue> get(VariableScope scope, String name) {
        return Optional.ofNullable(committed.get(scope).get(name));
    }

    @Override
    public StateMutation beginLocal() {
        return new Buffer();
    }

    @Override
    public void commit(StateMutation mutation, StepOutcomeType outcome) {
        if (!(mutation instanceof Buffer buffer)) {
            throw new IllegalArgumentException("unknown mutation");
        }
        boolean commit = outcome == StepOutcomeType.SUCCESS
                || (outcome == StepOutcomeType.CHALLENGE && buffer.commitOnChallenge);
        if (commit) {
            buffer.pending.forEach((scope, values) -> committed.get(scope).putAll(values));
        }
        buffer.pending.clear();
    }

    @Override
    public void discard(StateMutation mutation) {
        if (mutation instanceof Buffer buffer) {
            buffer.pending.clear();
        }
    }

    /**
     * Replaces SESSION materials from a bound session (lookup or waiter re-read).
     *
     * @param materials SESSION values
     */
    public void bindSession(Map<String, DataValue> materials) {
        committed.get(VariableScope.SESSION).clear();
        if (materials != null) {
            committed.get(VariableScope.SESSION).putAll(materials);
        }
    }

    /**
     * @return committed SESSION values
     */
    public Map<String, DataValue> sessionMaterials() {
        return Map.copyOf(committed.get(VariableScope.SESSION));
    }

    /**
     * Clears a writable scope (FLOW at the start of an authentication invocation).
     *
     * @param scope scope to clear
     */
    public void clearScope(VariableScope scope) {
        if (scope == VariableScope.GLOBAL) {
            throw new IllegalStateException("VAL_GLOBAL_WRITE");
        }
        committed.get(scope).clear();
    }

    /**
     * Marks the current buffer as allowed to commit on CHALLENGE.
     *
     * @param mutation buffer
     */
    public static void allowChallengeCommit(StateMutation mutation) {
        if (mutation instanceof Buffer buffer) {
            buffer.commitOnChallenge = true;
        }
    }

    private static final class Buffer implements StateMutation {
        private final EnumMap<VariableScope, Map<String, DataValue>> pending = new EnumMap<>(VariableScope.class);
        private boolean commitOnChallenge;

        private Buffer() {
            for (VariableScope scope : VariableScope.values()) {
                pending.put(scope, new HashMap<>());
            }
        }

        @Override
        public void set(VariableScope scope, String name, DataValue value) {
            Objects.requireNonNull(scope, "scope");
            if (scope == VariableScope.GLOBAL) {
                throw new IllegalStateException("VAL_GLOBAL_WRITE");
            }
            pending.get(scope).put(name, value);
        }
    }
}
