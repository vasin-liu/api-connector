/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.auth.profile;

import com.suntek.integration.auth.context.AuthContext;
import com.suntek.integration.auth.context.AuthOutcome;
import com.suntek.integration.auth.spi.AuthProvider;

/**
 * 无认证 Profile。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public class NoneAuthProvider implements AuthProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String profileType() {
        return "none";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthOutcome apply(AuthContext context) {
        return AuthOutcome.empty();
    }
}
