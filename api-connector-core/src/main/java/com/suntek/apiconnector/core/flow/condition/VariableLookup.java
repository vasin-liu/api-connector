/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;

import java.util.Optional;

/**
 * Read-only view of committed VariableRuntime values.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
@FunctionalInterface
public interface VariableLookup {

    /**
     * @param scope variable scope
     * @param name  variable name
     * @return committed value, empty if undefined
     */
    Optional<DataValue> get(VariableScope scope, String name);

    /**
     * @return lookup that never finds a variable
     */
    static VariableLookup empty() {
        return (scope, name) -> Optional.empty();
    }
}
