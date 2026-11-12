/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sm3HasherTest {

    private static final byte[] R1_MATERIAL =
            "test-app-idtest-app-secret1000n1".getBytes(StandardCharsets.UTF_8);
    private static final String R1_HEX =
            "bce0b91e9ba1e45aae2ccd3341c7f5bcf0889e2b08d12ed7b3cd6dc9744c05cb";

    @Test
    void hexIsLowercaseSm3OfPublishedMaterial() {
        assertThat(Sm3Hasher.hex(R1_MATERIAL)).isEqualTo(R1_HEX);
        assertThat(Sm3Hasher.encode(R1_MATERIAL, DigestEncoding.HEX)).isEqualTo(R1_HEX);
    }

    @Test
    void hexUpperIsNotLowercaseDigest() {
        assertThat(Sm3Hasher.encode(R1_MATERIAL, DigestEncoding.HEX_UPPER))
                .isEqualTo(R1_HEX.toUpperCase());
        assertThat(Sm3Hasher.encode(R1_MATERIAL, DigestEncoding.HEX_UPPER))
                .isNotEqualTo(R1_HEX);
    }

    @Test
    void base64IsNotHexDigest() {
        assertThat(Sm3Hasher.encode(R1_MATERIAL, DigestEncoding.BASE64))
                .isNotEqualTo(R1_HEX);
        assertThat(Sm3Hasher.encode(R1_MATERIAL, DigestEncoding.BASE64))
                .isNotEqualTo(R1_HEX.toUpperCase());
    }
}
