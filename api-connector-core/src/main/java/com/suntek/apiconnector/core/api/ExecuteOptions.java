/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

import java.time.Duration;
import java.util.Map;

/**
 * Execute options.
 *
 * @param timeout      execution timeout
 * @param allowReplay  cannot override UNSAFE/UNKNOWN defaults
 * @param traceBaggage non-secret trace fields
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecuteOptions(
        Duration timeout,
        boolean allowReplay,
        Map<String, String> traceBaggage
) {
}
