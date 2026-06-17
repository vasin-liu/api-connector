/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.transform;

import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * SM4 block cipher helper — {@code SM4/ECB/PKCS5Padding} + Base64 over the BouncyCastle provider,
 * matching legacy CETC {@code CetcUtils} parity (ADR-002, 02-RESEARCH SM4 notes).
 *
 * <p>UTF-8 plaintext in, Base64 ciphertext out for encrypt; the inverse for decrypt. The SM4 key
 * is the UTF-8 bytes of the resolved credential (16 bytes / 128-bit).</p>
 */
final class Sm4Cipher {

    private static final String TRANSFORM = "SM4/ECB/PKCS5Padding";
    private static final String ALGORITHM = "SM4";
    private static final String PROVIDER = "BC";
    private static final int KEY_LENGTH_BYTES = 16;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Cipher() {
    }

    static String encryptToBase64(String type, String plaintext, String key) {
        byte[] cipherBytes = doFinal(type, Cipher.ENCRYPT_MODE, key,
                plaintext == null ? new byte[0] : plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipherBytes);
    }

    static String decryptFromBase64(String type, String ciphertextBase64, String key) {
        byte[] cipherBytes;
        try {
            cipherBytes = Base64.getDecoder().decode(
                    ciphertextBase64 == null ? "" : ciphertextBase64);
        } catch (IllegalArgumentException ex) {
            throw MappingExceptions.transformFailed(type, "input is not valid Base64", ex);
        }
        byte[] plainBytes = doFinal(type, Cipher.DECRYPT_MODE, key, cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private static byte[] doFinal(String type, int mode, String key, byte[] input) {
        byte[] keyBytes = keyBytes(type, key);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM, PROVIDER);
            cipher.init(mode, new SecretKeySpec(keyBytes, ALGORITHM));
            return cipher.doFinal(input);
        } catch (Exception ex) {
            throw MappingExceptions.transformFailed(type, ex.getMessage(), ex);
        }
    }

    private static byte[] keyBytes(String type, String key) {
        if (key == null || key.isEmpty()) {
            throw MappingExceptions.transformFailed(type, "SM4 key is empty", null);
        }
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw MappingExceptions.transformFailed(
                    type, "SM4 key must be 16 bytes (UTF-8), got " + keyBytes.length, null);
        }
        return keyBytes;
    }
}
