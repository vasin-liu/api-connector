/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.auth.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link AkskCanonicalSigner}.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
class AkskCanonicalSignerTest {

    @Test
    void buildCanonicalQueryString_sortsAndEncodes() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("b", "2");
        query.put("a", "1");
        String canonical = AkskCanonicalSigner.buildCanonicalQueryString(query);
        assertEquals("a=1&b=2", canonical);
    }

    @Test
    void sign_producesRequiredHeaders() {
        Map<String, String> headers = AkskCanonicalSigner.sign(
                "GET",
                "/api/v2/demo",
                Map.of("page", "1"),
                "test-ak",
                "test-sk");
        assertEquals("test-ak", headers.get("X-Auth-Key"));
        assertEquals(AkskCanonicalSigner.ALGORITHM_NAME, headers.get("X-Auth-Algorithm"));
        assertNotNull(headers.get("X-Auth-Signature"));
        assertNotNull(headers.get("X-Auth-Timestamp"));
        assertNotNull(headers.get("X-Auth-SnowflakeID"));
    }
}
