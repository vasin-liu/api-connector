/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.transform;

import com.suntek.apiconnector.mapping.TransformContext;
import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.TransformStep;

/**
 * SM4 body decryption transform — inverse of {@link Sm4EncryptTransformStep}, used on the response
 * direction (D-20, D-21). Base64 ciphertext in, UTF-8 plaintext out.
 */
public final class Sm4DecryptTransformStep implements TransformStep {

    public static final String TYPE = "sm4_decrypt";
    public static final String KEY_REF = "keyRef";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String apply(TransformContext ctx) {
        String key = resolveKey(ctx);
        return Sm4Cipher.decryptFromBase64(TYPE, ctx.body(), key);
    }

    private static String resolveKey(TransformContext ctx) {
        String keyRef = ctx.config(KEY_REF);
        if (keyRef == null || keyRef.isBlank()) {
            throw MappingExceptions.transformKeyMissing(TYPE, null);
        }
        String key = ctx.credentials().get(keyRef);
        if (key == null || key.isEmpty()) {
            throw MappingExceptions.transformKeyMissing(TYPE, keyRef);
        }
        return key;
    }
}
