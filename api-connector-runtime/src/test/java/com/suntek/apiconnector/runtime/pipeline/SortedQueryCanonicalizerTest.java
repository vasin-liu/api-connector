/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SortedQueryCanonicalizerTest {

    static final String CANONICAL = "city=110000&key=test-ak&timestamp=1000";
    static final String SHUFFLED = "timestamp=1000&key=test-ak&city=110000";
    static final String KEY = "test-sk";
    static final String VECTOR = "7de16153d564bf1afd3b97d75f6c771a4bd7e7ac88d8d1648608b10241056155";

    @Test
    void publishedVectorMatchesSortedCanonicalString() {
        assertThat(HmacSha256.hexUtf8(KEY, CANONICAL)).isEqualTo(VECTOR);
    }

    @Test
    void shuffledKeyOrderWithoutSortDoesNotMatchVector() {
        assertThat(HmacSha256.hexUtf8(KEY, SHUFFLED)).isNotEqualTo(VECTOR);
        assertThat(SHUFFLED).isNotEqualTo(CANONICAL);
    }

    @Test
    void sortsShuffledMapAndDropsSig() {
        Map<String, String> shuffled = new LinkedHashMap<>();
        shuffled.put("timestamp", "1000");
        shuffled.put("sig", "should-not-appear");
        shuffled.put("key", "test-ak");
        shuffled.put("city", "110000");
        assertThat(SortedQueryCanonicalizer.canonicalize(shuffled, List.of("sig"), "&"))
                .isEqualTo(CANONICAL);
        assertThat(HmacSha256.hexUtf8(KEY, SortedQueryCanonicalizer.canonicalize(shuffled, List.of("sig"), "&")))
                .isEqualTo(VECTOR);
    }

    @Test
    void omittedEncodingMatchesNonePlaintextVector() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", "1000");
        params.put("key", "test-ak");
        params.put("city", "110000");
        assertThat(SortedQueryCanonicalizer.canonicalize(params, List.of(), "&"))
                .isEqualTo(CANONICAL);
        assertThat(SortedQueryCanonicalizer.canonicalize(
                params, List.of(), "&", SortedQueryCanonicalizer.Encoding.NONE))
                .isEqualTo(CANONICAL);
    }

    @Test
    void rfc3986EncodesThenSortsSpaceStarAndTilde() {
        Map<String, String> shuffled = new LinkedHashMap<>();
        shuffled.put("q", "a b");
        shuffled.put("note", "~ok");
        shuffled.put("city", "110000");
        shuffled.put("star", "a*b");
        String canonical = SortedQueryCanonicalizer.canonicalize(
                shuffled, List.of(), "&", SortedQueryCanonicalizer.Encoding.RFC3986);
        assertThat(canonical).isEqualTo("city=110000&note=~ok&q=a%20b&star=a%2Ab");
        assertThat(canonical).contains("%20");
        assertThat(canonical).contains("%2A");
        assertThat(canonical).contains("~ok");
        assertThat("q=a%20b&star=a%2Ab&note=~ok&city=110000").isNotEqualTo(canonical);
    }

    @Test
    void rfc3986IdpsQueryLineMatchesPublishedVector() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", "a*b");
        params.put("city", "110000");
        assertThat(SortedQueryCanonicalizer.canonicalize(
                params, List.of(), "&", SortedQueryCanonicalizer.Encoding.RFC3986))
                .isEqualTo("city=110000&q=a%2Ab");
    }
}
