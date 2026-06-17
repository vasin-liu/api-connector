/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.spi;

import com.suntek.apiconnector.domain.model.MappingContext;

/**
 * Groovy mapping script entry contract (D-12).
 */
@FunctionalInterface
public interface MappingScript {

    Object apply(MappingContext ctx);
}
