/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.time;

import java.util.UUID;

/**
 * Supplies client-generated nonces for {@code generate: nonce}.
 *
 * @author Gensokyo
 * @since 2026-09-20
 */
@FunctionalInterface
public interface NonceSource {

    /**
     * @return next nonce; successive calls MUST be allowed to differ
     */
    String next();

    /**
     * @return UUID-based source
     */
    static NonceSource uuid() {
        return () -> UUID.randomUUID().toString();
    }
}
