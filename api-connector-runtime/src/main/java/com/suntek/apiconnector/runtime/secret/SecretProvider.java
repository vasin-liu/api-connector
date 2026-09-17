/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import java.util.Optional;

/**
 * Looks up secret material by provider reference. Must not log resolved bytes.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
@FunctionalInterface
public interface SecretProvider {

    /**
     * @param secretRef provider reference
     * @return material when present
     */
    Optional<byte[]> get(String secretRef);
}
