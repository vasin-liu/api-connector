/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sm4EcbCipherTest {

    private static final byte[] PLAIN =
            ("{\"alarmNo\":\"a1\",\"alarmTime\":\"2026-01-01 00:00:00\",\"deviceCode\":\"dev-1\","
                    + "\"factory\":\"test\",\"picPath\":\"https://cetc.example/p\",\"type\":\"98\","
                    + "\"videoPath\":\"https://cetc.example/v\"}")
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "test-cetc-secret".getBytes(StandardCharsets.UTF_8);
    private static final String PUBLISHED_BASE64 =
            "ieP0Z+CKWzAf5dy1uj3EY2FPwwZbx7DOjMmmKpVCXKMYpAwXedm8DC90DHaOKE1Wyo6ohUZC405x1+UEOX8Otm1ViHmaVxXMBwKuxymJGN+q+offL+rjEC95bML/Ft41XqC/ytDCXvFPXMqkReaxNRkKTJ49FYwmwe24hp+4v6FNdDb5L5U7VGYmFm/RbdDQocEAlh+zzRK7mYVuS3edjOLrAaLF8MXjsRa/0n2q87g=";

    @Test
    void base64MatchesPublishedVector() {
        assertThat(Sm4EcbCipher.encode(KEY, PLAIN, CipherEncoding.BASE64)).isEqualTo(PUBLISHED_BASE64);
        assertThat(Sm4EcbCipher.encode(KEY, PLAIN, null)).isEqualTo(PUBLISHED_BASE64);
    }

    @Test
    void hexEncodingIsRejected() {
        assertThatThrownBy(() -> CipherEncoding.parse("hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown cipher encoding");
    }

    @Test
    void nonSixteenByteKeyFailsWithoutKeyInMessage() {
        byte[] shortKey = "short-key".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Sm4EcbCipher.encode(shortKey, PLAIN, CipherEncoding.BASE64))
                .isInstanceOf(ExecuteException.class)
                .satisfies(ex -> {
                    assertThat(((ExecuteException) ex).code()).isEqualTo(Sm4EcbCipher.SM4_KEY_LENGTH);
                    assertThat(ex.getMessage()).doesNotContain("short-key");
                    assertThat(ex.getMessage()).doesNotContain("test-cetc-secret");
                });
    }
}
