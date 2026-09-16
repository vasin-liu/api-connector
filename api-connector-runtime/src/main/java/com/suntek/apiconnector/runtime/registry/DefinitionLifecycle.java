/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.registry;

/**
 * Definition lifecycle. Execute is allowed only for {@link #PUBLISHED}.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public enum DefinitionLifecycle {
    PUBLISHED,
    DRAFT,
    DISABLED
}
