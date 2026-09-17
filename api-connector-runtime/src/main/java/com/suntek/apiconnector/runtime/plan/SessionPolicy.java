/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.time.Duration;
import java.util.Optional;

/**
 * Session policy. Absent when the definition has no session block.
 *
 * @param ttl              session TTL
 * @param failureCooldown  shared failure cooldown
 * @param cookiesEnabled   whether a CookieStore is used
 * @author Gensokyo
 * @since 2026-09-14
 */
public record SessionPolicy(Optional<Duration> ttl, Optional<Duration> failureCooldown, boolean cookiesEnabled) {

    /**
     * @return no session policy
     */
    public static SessionPolicy none() {
        return new SessionPolicy(Optional.empty(), Optional.empty(), false);
    }
}
