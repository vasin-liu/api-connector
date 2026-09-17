/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.validate;

/**
 * One validation violation. Returned as a set; Validate is not fail-fast.
 *
 * @param code    stable code from {@link ValidationCodes}
 * @param path    JSON Pointer into the definition
 * @param message human-readable detail
 * @author Gensokyo
 * @since 2026-09-14
 */
public record Violation(String code, String path, String message) {
}
