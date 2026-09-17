/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import java.time.Duration;
import java.util.Locale;

/**
 * Parses definition duration scalars such as {@code 10s} and {@code 30m}.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class DurationParser {

    private DurationParser() {
    }

    /**
     * @param raw duration text
     * @return duration
     */
    public static Duration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("duration must not be blank");
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)));
        }
        if (text.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
        }
        if (text.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(text.substring(0, text.length() - 1)));
        }
        if (text.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(text.substring(0, text.length() - 1)));
        }
        throw new IllegalArgumentException("unsupported duration: " + raw);
    }
}
