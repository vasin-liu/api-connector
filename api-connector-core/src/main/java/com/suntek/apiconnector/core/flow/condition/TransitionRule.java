/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.flow.TransitionAction;

/**
 * One compiled transition: condition plus action. {@code then} is ignored by first-match.
 *
 * @param when   condition
 * @param action selected action when {@code when} matches
 * @author Gensokyo
 * @since 2026-09-14
 */
public record TransitionRule(Condition when, TransitionAction action) {
}
