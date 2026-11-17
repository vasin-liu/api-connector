/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Percent-encodes a whole byte string with RFC 3986 unreserved rules.
 *
 * @author Gensokyo
 * @since 2026-11-16
 */
public final class PercentEncodeCanonicalizer {

    private PercentEncodeCanonicalizer() {
    }

    /**
     * @param data UTF-8 bytes of the raw string
     * @return UTF-8 bytes of the percent-encoded string
     */
    public static byte[] encode(byte[] data) {
        Objects.requireNonNull(data, "data");
        String raw = new String(data, StandardCharsets.UTF_8);
        return SortedQueryCanonicalizer.uriEncode(raw).getBytes(StandardCharsets.UTF_8);
    }
}
