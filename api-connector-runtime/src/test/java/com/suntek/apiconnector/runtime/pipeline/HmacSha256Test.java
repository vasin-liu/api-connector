/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256Test {

    @Test
    void rfc4231TestCase2LowercaseHex() {
        assertThat(HmacSha256.hexUtf8("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void mockCChallengeVectorUsesValueRefAsKeyUntilCredentialResolver() {
        String key = "secret/mock-c/api-key";
        String nonce = "nonce-1";
        String timestamp = "1001";
        assertThat(HmacSha256.hexUtf8(key, key + nonce + timestamp))
                .isEqualTo(HmacSha256.hexUtf8(key, "secret/mock-c/api-keynonce-11001"));
        assertThat(HmacSha256.hexUtf8(key, key + nonce + timestamp))
                .isNotEqualTo(HmacSha256.hexUtf8(key, key + "nonce-2" + timestamp));
    }
}
