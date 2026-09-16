/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow;

/**
 * Variable scopes. GLOBAL is read-only at runtime.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public enum VariableScope {
    GLOBAL,
    SESSION,
    EXECUTION,
    FLOW,
    LOCAL
}
