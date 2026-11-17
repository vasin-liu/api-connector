/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PercentEncodeCanonicalizerTest {

    @Test
    void encodesPublishedCanonicalPreimage() {
        byte[] encoded = PercentEncodeCanonicalizer.encode(
                "{\"plateNo\":\"A12345\"}&_t=1700000000000&symKey=symkey-fixture"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .isEqualTo("%7B%22plateNo%22%3A%22A12345%22%7D%26_t%3D1700000000000%26symKey%3Dsymkey-fixture");
    }

    @Test
    void spaceAndTildeFollowRfc3986Unreserved() {
        assertThat(new String(PercentEncodeCanonicalizer.encode("a b~".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)).isEqualTo("a%20b~");
    }

    @Test
    void encodesSeparatorsInsideTheString() {
        assertThat(new String(PercentEncodeCanonicalizer.encode("a&b=c".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)).isEqualTo("a%26b%3Dc");
    }
}
