/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import org.bouncycastle.crypto.digests.SM3Digest;

import java.util.Objects;

/**
 * SM3 hasher. Default pipeline encoding is lowercase hex.
 *
 * @author Gensokyo
 * @since 2026-11-12
 */
public final class Sm3Hasher {

    private Sm3Hasher() {
    }

    /**
     * @param data message bytes
     * @return raw SM3 digest
     */
    public static byte[] digest(byte[] data) {
        Objects.requireNonNull(data, "data");
        SM3Digest sm3 = new SM3Digest();
        sm3.update(data, 0, data.length);
        byte[] out = new byte[sm3.getDigestSize()];
        sm3.doFinal(out, 0);
        return out;
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
