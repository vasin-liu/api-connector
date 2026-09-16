/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.observe.DecisionTrace;
import com.suntek.apiconnector.core.session.SessionSnapshot;

import java.util.Optional;

/**
 * Terminal result of one execution.
 *
 * @param outcome    top-level step outcome, not HTTP status
 * @param httpStatus last completed transport status
 * @param body       last response body
 * @param trace      redacted decision trace
 * @param session    session after the execution if any
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecutionResult(
        StepOutcomeType outcome,
        Optional<Integer> httpStatus,
        ResponseBody body,
        DecisionTrace trace,
        Optional<SessionSnapshot> session
) {
}
