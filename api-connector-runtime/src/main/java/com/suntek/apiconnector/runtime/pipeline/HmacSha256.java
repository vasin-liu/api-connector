/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/**
 * HMAC-SHA256 signer. Output is lowercase hex.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class HmacSha256 {

    private HmacSha256() {
    }

    /**
     * @param key  HMAC key
     * @param data message
     * @return lowercase hex digest
     */
    public static String hex(byte[] key, byte[] data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * @param key  UTF-8 key
     * @param data UTF-8 message
     * @return lowercase hex digest
     */
    public static String hexUtf8(String key, String data) {
        return hex(key.getBytes(StandardCharsets.UTF_8), data.getBytes(StandardCharsets.UTF_8));
    }
}
