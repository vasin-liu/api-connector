/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.observe;

import java.util.Map;

/**
 * One decision. Facts must already be redacted.
 *
 * @param decisionId unique id
 * @param type       CONDITION, CHALLENGE, RETRY, REPLAY, SESSION, PIPELINE, AUTH_TRIGGER
 * @param action     selected action
 * @param reasonCode stable reason
 * @param facts      redacted facts
 * @author Gensokyo
 * @since 2026-09-14
 */
public record DecisionRecord(
        String decisionId,
        String type,
        String action,
        String reasonCode,
        Map<String, String> facts
) {
}
