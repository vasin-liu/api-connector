/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.session;

/**
 * Session reuse key. Revision is intentionally absent.
 *
 * @param apiId         api / definition id
 * @param authProfile   auth profile id
 * @param credentialRef credential name
 * @author Gensokyo
 * @since 2026-09-15
 */
public record SessionLookupKey(String apiId, String authProfile, String credentialRef) {
}
