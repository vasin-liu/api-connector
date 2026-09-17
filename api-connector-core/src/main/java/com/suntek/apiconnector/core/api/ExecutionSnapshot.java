/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

import java.time.Instant;

/**
 * Immutable binding of one execution to a compiled plan.
 *
 * @param executionId         unique execution id
 * @param apiId               definition id
 * @param definitionRevision  revision executed
 * @param planId              compiled plan id
 * @param startedAt           start timestamp
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecutionSnapshot(
        String executionId,
        String apiId,
        String definitionRevision,
        String planId,
        Instant startedAt
) {
}
