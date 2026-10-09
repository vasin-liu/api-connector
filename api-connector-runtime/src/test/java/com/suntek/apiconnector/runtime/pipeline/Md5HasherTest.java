/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Md5HasherTest {

    @Test
    void hexIsLowercaseMd5() {
        assertThat(Md5Hasher.hex("test-pass".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("380e5dc89564f30713ad54bf06aacea8");
    }

    @Test
    void base64IsRawDigestNotHex() {
        byte[] data = "test-pass".getBytes(StandardCharsets.UTF_8);
        assertThat(Md5Hasher.encode(data, DigestEncoding.BASE64))
                .isEqualTo("OA5dyJVk8wcTrVS/BqrOqA==");
        assertThat(Md5Hasher.encode(data, DigestEncoding.BASE64))
                .isNotEqualTo(Md5Hasher.hex(data));
        assertThat(Md5Hasher.encode(data, DigestEncoding.HEX))
                .isEqualTo(Md5Hasher.hex(data));
    }
}
