/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves {@code secret/a/b} from environment variable {@code SECRET_A_B}.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class EnvironmentSecretProvider implements SecretProvider {

    @Override
    public Optional<byte[]> get(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return Optional.empty();
        }
        String key = secretRef.toUpperCase(Locale.ROOT).replace('/', '_').replace('-', '_').replace('.', '_');
        String value = System.getenv(key);
        return value == null ? Optional.empty() : Optional.of(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
