/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.session.SessionKey;
import com.suntek.apiconnector.core.value.DataValue;

import java.util.Map;

/**
 * Result of a coordinated authentication attempt.
 *
 * @param success  whether authentication succeeded
 * @param outcome  terminal outcome when failed
 * @param generation session generation after success, or previous generation on failure
 * @author Gensokyo
 * @since 2026-09-15
 */
public record AuthOutcome(boolean success, StepOutcomeType outcome, long generation) {

    /**
     * @param generation new generation
     * @return success
     */
    public static AuthOutcome succeeded(long generation) {
        return new AuthOutcome(true, StepOutcomeType.SUCCESS, generation);
    }

    /**
     * @param outcome    shared failure outcome
     * @param generation unchanged generation
     * @return failure
     */
    public static AuthOutcome failed(StepOutcomeType outcome, long generation) {
        return new AuthOutcome(false, outcome, generation);
    }
}
