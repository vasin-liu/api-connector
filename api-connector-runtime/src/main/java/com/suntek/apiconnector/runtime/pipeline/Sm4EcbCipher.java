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

import java.util.Arrays;
import java.util.Objects;

/**
 * SM4/ECB/PKCS5Padding encryptor. Default pipeline encoding is Base64.
 *
 * @author Gensokyo
 * @since 2026-11-13
 */
public final class Sm4EcbCipher {

    public static final String SM4_KEY_LENGTH = "SM4_KEY_LENGTH";

    private static final int KEY_LENGTH = 16;

    private Sm4EcbCipher() {
    }

    /**
     * @param key  16-byte SM4 key
     * @param data plaintext bytes
     * @return ciphertext bytes
     */
    public static byte[] encrypt(byte[] key, byte[] data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        if (key.length != KEY_LENGTH) {
            throw new ExecuteException(SM4_KEY_LENGTH, "SM4 key must be 16 bytes");
        }
        try {
            PaddedBufferedBlockCipher cipher =
                    new PaddedBufferedBlockCipher(new SM4Engine(), new PKCS7Padding());
            cipher.init(true, new KeyParameter(key));
            byte[] out = new byte[cipher.getOutputSize(data.length)];
            int n = cipher.processBytes(data, 0, data.length, out, 0);
            n += cipher.doFinal(out, n);
            return Arrays.copyOf(out, n);
        } catch (InvalidCipherTextException e) {
            throw new ExecuteException(SM4_KEY_LENGTH, "SM4 encryption failed");
        }
    }

    /**
     * @param key      16-byte SM4 key
     * @param data     plaintext bytes
     * @param encoding ciphertext encoding
     * @return encoded ciphertext
     */
    public static String encode(byte[] key, byte[] data, CipherEncoding encoding) {
        CipherEncoding mode = encoding == null ? CipherEncoding.BASE64 : encoding;
        return mode.format(encrypt(key, data));
    }
}
