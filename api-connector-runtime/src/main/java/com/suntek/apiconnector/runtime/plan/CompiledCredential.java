/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.Optional;

/**
 * Compiled credential declaration. Resolved material is not stored on the plan.
 *
 * @param name        credential name (credentialRef)
 * @param type        username-password, secret, ...
 * @param usernameRef optional username secret ref
 * @param passwordRef optional password secret ref
 * @param valueRef    optional single-value secret ref
 * @param apiId       owning api id
 * @author Gensokyo
 * @since 2026-09-15
 */
public record CompiledCredential(
        String name,
        String type,
        Optional<String> usernameRef,
        Optional<String> passwordRef,
        Optional<String> valueRef,
        String apiId
) {
}
