/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Digest string encoding for HMAC and MD5 pipeline nodes.
 *
 * @author Gensokyo
 * @since 2026-10-06
 */
public enum DigestEncoding {

    HEX,
    BASE64;

    /**
     * @param digest raw digest bytes
     * @return encoded string
     */
    public String format(byte[] digest) {
        byte[] data = digest == null ? new byte[0] : digest;
        return switch (this) {
            case HEX -> HexFormat.of().formatHex(data);
            case BASE64 -> Base64.getEncoder().encodeToString(data);
        };
    }

    /**
     * @param raw YAML {@code encoding} value; {@code null} or blank is {@link #HEX}
     * @return encoding
     * @throws IllegalArgumentException when the value is not {@code hex} or {@code base64}
     */
    public static DigestEncoding parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HEX;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "hex" -> HEX;
            case "base64" -> BASE64;
            default -> throw new IllegalArgumentException("unknown digest encoding: " + raw);
        };
    }
}
