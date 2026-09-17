/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.state;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;

/**
 * Runtime variable store with commit/discard.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public interface VariableRuntime {

    /**
     * @param scope scope
     * @param name  name
     * @return committed value, or empty if undefined
     */
    java.util.Optional<DataValue> get(VariableScope scope, String name);

    /**
     * @return a mutation buffer for the current step
     */
    StateMutation beginLocal();

    /**
     * Commits a mutation when the step outcome allows it.
     *
     * @param mutation pending mutation
     * @param outcome  step outcome
     */
    void commit(StateMutation mutation, StepOutcomeType outcome);

    /**
     * Discards a mutation.
     *
     * @param mutation pending mutation
     */
    void discard(StateMutation mutation);

    /**
     * Buffered writes for one step.
     */
    interface StateMutation {

        /**
         * @param scope scope; GLOBAL is rejected
         * @param name  name
         * @param value value
         */
        void set(VariableScope scope, String name, DataValue value);
    }
}
