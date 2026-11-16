/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;

/**
 * RSA/ECB/PKCS1Padding encryptor. Key bytes are UTF-8 of an X.509 SPKI Base64 string.
 * Default pipeline encoding is Base64. PKCS1 v1.5 padding is random.
 *
 * @author Gensokyo
 * @since 2026-11-16
 */
public final class RsaPkcs1Cipher {

    public static final String RSA_KEY_INVALID = "RSA_KEY_INVALID";

    private RsaPkcs1Cipher() {
    }

    /**
     * @param spkiBase64Utf8 UTF-8 of an X.509 SubjectPublicKeyInfo Base64 string
     * @param data           plaintext bytes
     * @return ciphertext bytes
     */
    public static byte[] encrypt(byte[] spkiBase64Utf8, byte[] data) {
        Objects.requireNonNull(spkiBase64Utf8, "key");
        Objects.requireNonNull(data, "data");
        RSAPublicKey publicKey = parsePublicKey(spkiBase64Utf8);
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new ExecuteException(RSA_KEY_INVALID, "RSA encryption failed");
        }
    }

    /**
     * @param spkiBase64Utf8 UTF-8 of an X.509 SubjectPublicKeyInfo Base64 string
     * @param data           plaintext bytes
     * @param encoding       ciphertext encoding
     * @return encoded ciphertext
     */
    public static String encode(byte[] spkiBase64Utf8, byte[] data, CipherEncoding encoding) {
        CipherEncoding mode = encoding == null ? CipherEncoding.BASE64 : encoding;
        return mode.format(encrypt(spkiBase64Utf8, data));
    }

    private static RSAPublicKey parsePublicKey(byte[] spkiBase64Utf8) {
        String text = new String(spkiBase64Utf8, StandardCharsets.UTF_8).trim();
        try {
            byte[] der = Base64.getDecoder().decode(text);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new ExecuteException(RSA_KEY_INVALID, "RSA public key is invalid");
        }
    }
}
