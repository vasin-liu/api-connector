/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.flow.TransitionAction;

import java.util.List;
import java.util.Optional;

/**
 * First-match transition selection. List order is priority; the compiler must not reorder by action.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class TransitionEvaluator {

    private TransitionEvaluator() {
    }

    /**
     * @param context last response plus variables
     * @param rules   transitions in definition order
     * @return first matching index and action, or empty when none match
     */
    public static Optional<MatchedTransition> firstMatch(ConditionContext context, List<TransitionRule> rules) {
        for (int i = 0; i < rules.size(); i++) {
            TransitionRule rule = rules.get(i);
            if (ConditionEvaluator.matches(rule.when(), context)) {
                return Optional.of(new MatchedTransition(i, rule.action()));
            }
        }
        return Optional.empty();
    }

    /**
     * @param index  0-based transition index
     * @param action selected action
     */
    public record MatchedTransition(int index, TransitionAction action) {
    }
}
