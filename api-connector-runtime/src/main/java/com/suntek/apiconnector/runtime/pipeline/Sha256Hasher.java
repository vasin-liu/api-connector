/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.security.MessageDigest;
import java.util.Objects;

/**
 * SHA-256 hasher. Default pipeline encoding is lowercase hex.
 *
 * @author Gensokyo
 * @since 2026-11-17
 */
public final class Sha256Hasher {

    private Sha256Hasher() {
    }

    /**
     * @param data message bytes
     * @return raw SHA-256 digest
     */
    public static byte[] digest(byte[] data) {
        Objects.requireNonNull(data, "data");
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    /**
     * @param data message bytes
     * @return lowercase hex digest
     */
    public static String hex(byte[] data) {
        return DigestEncoding.HEX.format(digest(data));
    }

    /**
     * @param data     message bytes
     * @param encoding digest encoding
     * @return encoded digest
     */
    public static String encode(byte[] data, DigestEncoding encoding) {
        DigestEncoding mode = encoding == null ? DigestEncoding.HEX : encoding;
        return mode.format(digest(data));
    }
}
