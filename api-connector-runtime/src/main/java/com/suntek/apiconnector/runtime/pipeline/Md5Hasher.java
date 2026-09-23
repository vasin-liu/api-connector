/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * MD5 hasher. Output is lowercase hex.
 *
 * @author Gensokyo
 * @since 2026-09-22
 */
public final class Md5Hasher {

    private Md5Hasher() {
    }

    /**
     * @param data message bytes
     * @return lowercase hex digest
     */
    public static String hex(byte[] data) {
        Objects.requireNonNull(data, "data");
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 failed", e);
        }
    }
}
