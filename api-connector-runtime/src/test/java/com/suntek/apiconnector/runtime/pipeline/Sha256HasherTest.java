/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256HasherTest {

    private static final byte[] R1_MATERIAL =
            "1700000000test-metro-token11700000000".getBytes(StandardCharsets.UTF_8);
    private static final String R1_HEX_UPPER =
            "F299939FA500E157E6EBD2899F6CE9FF926D0DF0996AC73D6643C501E62317E5";
    private static final String R1_HEX = R1_HEX_UPPER.toLowerCase();

    @Test
    void hexIsLowercaseSha256OfPublishedMaterial() {
        assertThat(Sha256Hasher.hex(R1_MATERIAL)).isEqualTo(R1_HEX);
        assertThat(Sha256Hasher.encode(R1_MATERIAL, DigestEncoding.HEX)).isEqualTo(R1_HEX);
    }

    @Test
    void hexUpperIsNotLowercaseDigest() {
        assertThat(Sha256Hasher.encode(R1_MATERIAL, DigestEncoding.HEX_UPPER)).isEqualTo(R1_HEX_UPPER);
        assertThat(Sha256Hasher.encode(R1_MATERIAL, DigestEncoding.HEX_UPPER)).isNotEqualTo(R1_HEX);
    }

    @Test
    void base64IsNotHexDigest() {
        assertThat(Sha256Hasher.encode(R1_MATERIAL, DigestEncoding.BASE64)).isNotEqualTo(R1_HEX);
        assertThat(Sha256Hasher.encode(R1_MATERIAL, DigestEncoding.BASE64)).isNotEqualTo(R1_HEX_UPPER);
    }
}
