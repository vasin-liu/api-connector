/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void artemisStringToSignIsBase64NotHex() {
        String stringToSign = "GET\n*/*\napplication/text;charset=UTF-8\nX-Ca-Key:test-ak\nX-Ca-Nonce:1\nX-Ca-Timestamp:1000\n/artemis/api/demo?size=20&start=0";
        assertThat(HmacSha256.hexUtf8("test-sk", stringToSign))
                .isEqualTo("f2f3396769f8dc74a90d1a4745b2893505601db8163fd13ed687192338e1134a");
        assertThat(HmacSha256.base64Utf8("test-sk", stringToSign))
                .isEqualTo("8vM5Z2n43HSpDRpHRbKJNQVgHbgWP9E+1ocZIzjhE0o=");
        assertThat(HmacSha256.base64Utf8("test-sk", stringToSign))
                .isNotEqualTo(HmacSha256.hexUtf8("test-sk", stringToSign));
    }

    @Test
    void unknownDigestEncodingIsRejected() {
        assertThatThrownBy(() -> DigestEncoding.parse("mystery"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown digest encoding");
        assertThat(DigestEncoding.parse(null)).isEqualTo(DigestEncoding.HEX);
        assertThat(DigestEncoding.parse("hex")).isEqualTo(DigestEncoding.HEX);
    }
}
