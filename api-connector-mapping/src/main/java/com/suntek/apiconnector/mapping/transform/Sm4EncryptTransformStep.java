/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.transform;

import com.suntek.apiconnector.mapping.TransformContext;
import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.TransformStep;

/**
 * SM4 body encryption transform — {@code SM4/ECB/PKCS5Padding} + Base64 (D-20, ADR-002).
 *
 * <p>Resolves the SM4 key from {@code stepConfig.keyRef} against connector credentials; an inline
 * {@code key} is never accepted (Pitfall 6 — enforced at publish by the spec validator).</p>
 */
public final class Sm4EncryptTransformStep implements TransformStep {

    public static final String TYPE = "sm4_encrypt";
    public static final String KEY_REF = "keyRef";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String apply(TransformContext ctx) {
        String key = resolveKey(ctx);
        return Sm4Cipher.encryptToBase64(TYPE, ctx.body(), key);
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
