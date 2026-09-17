/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

/**
 * Destination of a secret sink (the api that will receive the secret).
 *
 * @param apiId destination definition id
 * @author Gensokyo
 * @since 2026-09-15
 */
public record SinkDestination(String apiId) {
}
