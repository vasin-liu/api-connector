/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.spi;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.domain.model.AuthOutcome;

/**
 * Groovy auth script entry contract.
 */
@FunctionalInterface
public interface AuthScript {

    AuthOutcome apply(AuthContext ctx);
}
