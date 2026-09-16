/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.value;

/**
 * Approved consumer of secret bytes (HMAC, signer, authorization).
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
@FunctionalInterface
public interface SecretConsumer {

    /**
     * @param material secret bytes; caller must not retain after return
     */
    void accept(byte[] material);
}
