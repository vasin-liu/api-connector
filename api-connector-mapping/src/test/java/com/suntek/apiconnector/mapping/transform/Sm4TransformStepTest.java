/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.transform;

import com.suntek.apiconnector.mapping.TransformContext;
import com.suntek.apiconnector.mapping.exception.MappingErrorCode;
import com.suntek.apiconnector.mapping.exception.MappingException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * SM4 encrypt/decrypt round-trip, golden vector, and keyRef resolution tests (D-20, Pitfall 6).
 */
class Sm4TransformStepTest {

    private static final String KEY_REF = "appSecret";
    private static final String KEY = "1234567890abcdef";
    private static final String PLAINTEXT = "{\"name\":\"\u6e2c\u8a66\",\"id\":42}";

    // Golden vector: SM4/ECB/PKCS5Padding + Base64 over fixed 16-byte UTF-8 key and PLAINTEXT.
    private static final String EXPECTED_CIPHERTEXT = "p9iM6pdVe9bEVvicAs9v5EzB/7/lkEvGbEx7UQ7cxYc=";

    private final Sm4EncryptTransformStep encrypt = new Sm4EncryptTransformStep();
    private final Sm4DecryptTransformStep decrypt = new Sm4DecryptTransformStep();

    @Test
    void encryptThenDecryptRoundTripsToOriginal() {
        String cipher = encrypt.apply(ctx(PLAINTEXT, KEY_REF, "request", KEY));
        assertNotEquals(PLAINTEXT, cipher);

        String plain = decrypt.apply(ctx(cipher, KEY_REF, "response", KEY));
        assertEquals(PLAINTEXT, plain);
    }

    @Test
    void encryptIsDeterministicAndMatchesGoldenVector() {
        String first = encrypt.apply(ctx(PLAINTEXT, KEY_REF, "request", KEY));
        String second = encrypt.apply(ctx(PLAINTEXT, KEY_REF, "request", KEY));

        assertEquals(first, second);
        assertEquals(EXPECTED_CIPHERTEXT, first);
        assertEquals(PLAINTEXT, decrypt.apply(ctx(EXPECTED_CIPHERTEXT, KEY_REF, "response", KEY)));
    }

    @Test
    void missingCredentialForKeyRefThrowsStructuredError() {
        TransformContext ctx = new TransformContext(PLAINTEXT, Map.of("keyRef", KEY_REF), Map.of(), "request");

        MappingException ex = assertThrows(MappingException.class, () -> encrypt.apply(ctx));
        assertEquals(MappingErrorCode.TRANSFORM_KEY_MISSING, ex.code());
        assertEquals(KEY_REF, ex.details().get("keyRef"));
    }

    @Test
    void missingKeyRefConfigThrowsStructuredError() {
        TransformContext ctx = new TransformContext(PLAINTEXT, Map.of(), Map.of(KEY_REF, KEY), "request");

        MappingException ex = assertThrows(MappingException.class, () -> encrypt.apply(ctx));
        assertEquals(MappingErrorCode.TRANSFORM_KEY_MISSING, ex.code());
    }

    @Test
    void wrongKeyLengthFailsWithStructuredError() {
        TransformContext ctx = ctx(PLAINTEXT, KEY_REF, "request", "shortkey");

        MappingException ex = assertThrows(MappingException.class, () -> encrypt.apply(ctx));
        assertEquals(MappingErrorCode.TRANSFORM_FAILED, ex.code());
    }

    private static TransformContext ctx(String body, String keyRef, String direction, String key) {
        return new TransformContext(
                body,
                Map.of("type", "sm4_encrypt", "keyRef", keyRef, "direction", direction),
                Map.of(keyRef, key),
                direction);
    }
}
