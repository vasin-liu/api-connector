/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.util.Base64;
import java.util.Locale;

/**
 * Ciphertext string encoding for {@code cipher.sm4-ecb}.
 *
 * @author Gensokyo
 * @since 2026-11-13
 */
public enum CipherEncoding {

    BASE64;

    /**
     * @param ciphertext raw ciphertext bytes
     * @return encoded string
     */
    public String format(byte[] ciphertext) {
        byte[] data = ciphertext == null ? new byte[0] : ciphertext;
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * @param raw YAML {@code encoding} value; {@code null} or blank is {@link #BASE64}
     * @return encoding
     * @throws IllegalArgumentException when the value is not {@code base64}
     */
    public static CipherEncoding parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BASE64;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "base64" -> BASE64;
            default -> throw new IllegalArgumentException("unknown cipher encoding: " + raw);
        };
    }
}
