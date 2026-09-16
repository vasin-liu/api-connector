/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.core.flow.condition.Condition;

import java.util.Optional;

/**
 * Compiled transition. List order is priority.
 *
 * @param transitionId       t0..tN or explicit id
 * @param condition          AST
 * @param action             action
 * @param thenAction         AUTHENTICATE/REFRESH then
 * @param outcomeOverride    FAIL outcome override
 * @param retryFromStepId    RETRY_FLOW from
 * @param maxRequestAttempts from the step's request replay policy when present
 * @param sessionEffect      FAIL session status (AUTH_FAILED)
 * @author Gensokyo
 * @since 2026-09-14
 */
public record CompiledTransition(
        String transitionId,
        Condition condition,
        TransitionAction action,
        Optional<TransitionAction> thenAction,
        Optional<StepOutcomeType> outcomeOverride,
        Optional<String> retryFromStepId,
        int maxRequestAttempts,
        Optional<String> sessionEffect
) {
}
