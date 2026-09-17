/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.observe;

import java.util.List;

/**
 * Decision trace. Facts must already be redacted.
 *
 * @param decisions ordered decisions
 * @author Gensokyo
 * @since 2026-09-14
 */
public record DecisionTrace(List<DecisionRecord> decisions) {
}
