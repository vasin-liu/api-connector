/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.jsonpath;

/**
 * Thrown when a JSONPath expression uses a forbidden operator.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class ForbiddenJsonPathException extends IllegalArgumentException {

    /**
     * @param path the rejected expression
     */
    public ForbiddenJsonPathException(String path) {
        super("JSONPath profile forbids filters, recursive descent, and calls: " + path);
    }
}
