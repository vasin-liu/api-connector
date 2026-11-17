/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.api.ExecuteException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * SM4/ECB/PKCS5Padding decryptor for hex ciphertext and a 32-character hex key.
 *
 * @author Gensokyo
 * @since 2026-11-16
 */
public final class Sm4EcbDecryptCipher {

    public static final String SM4_DECRYPT_KEY = "SM4_DECRYPT_KEY";

    private static final int KEY_HEX_LENGTH = 32;

    private Sm4EcbDecryptCipher() {
    }

    /**
     * @param keyUtf8 UTF-8 of a 32-character hex key
     * @param data    UTF-8 of lowercase hex ciphertext
     * @return plaintext UTF-8 string
     */
    public static String decrypt(byte[] keyUtf8, byte[] data) {
        Objects.requireNonNull(keyUtf8, "key");
        Objects.requireNonNull(data, "data");
        byte[] key = decodeHexKey(keyUtf8);
        byte[] ciphertext = decodeHexCiphertext(data);
        try {
            PaddedBufferedBlockCipher cipher =
                    new PaddedBufferedBlockCipher(new SM4Engine(), new PKCS7Padding());
            cipher.init(false, new KeyParameter(key));
            byte[] out = new byte[cipher.getOutputSize(ciphertext.length)];
            int n = cipher.processBytes(ciphertext, 0, ciphertext.length, out, 0);
            n += cipher.doFinal(out, n);
            return new String(Arrays.copyOf(out, n), StandardCharsets.UTF_8);
        } catch (InvalidCipherTextException e) {
            throw new ExecuteException(SM4_DECRYPT_KEY, "SM4 decryption failed");
        }
    }

    private static byte[] decodeHexKey(byte[] keyUtf8) {
        String hex = new String(keyUtf8, StandardCharsets.UTF_8);
        if (hex.length() != KEY_HEX_LENGTH || !isHex(hex)) {
            throw new ExecuteException(SM4_DECRYPT_KEY, "SM4 decrypt key must be 32 hex characters");
        }
        return HexFormat.of().parseHex(hex.toLowerCase(Locale.ROOT));
    }

    private static byte[] decodeHexCiphertext(byte[] data) {
        String hex = new String(data, StandardCharsets.UTF_8);
        if (hex.isEmpty() || (hex.length() % 2) != 0 || !isHex(hex)) {
            throw new ExecuteException(SM4_DECRYPT_KEY, "SM4 decrypt ciphertext must be hex");
        }
        return HexFormat.of().parseHex(hex.toLowerCase(Locale.ROOT));
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
