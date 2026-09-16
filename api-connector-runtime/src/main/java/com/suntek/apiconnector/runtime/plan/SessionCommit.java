/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.Optional;

/**
 * Session mutation requested when an extract/assign step commits.
 *
 * @param sessionStatus       VALID, AUTH_FAILED, ...
 * @param incrementGeneration whether generation should increase
 * @author Gensokyo
 * @since 2026-09-15
 */
public record SessionCommit(Optional<String> sessionStatus, boolean incrementGeneration) {
}
