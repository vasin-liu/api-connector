/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.session;

import java.time.Instant;

/**
 * Observable session snapshot. Material is not included.
 *
 * @param apiId        api id
 * @param generation   session generation
 * @param status       VALID, FAILED, etc.
 * @param expiresAt    optional expiry
 * @author Gensokyo
 * @since 2026-09-14
 */
public record SessionSnapshot(
        String apiId,
        long generation,
        String status,
        Instant expiresAt
) {
}
