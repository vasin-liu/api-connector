/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.Map;
import java.util.Optional;

/**
 * Header or query binding after Normalize.
 *
 * @param name      original name
 * @param nameLower canonical lowercase name for headers
 * @param secretRef credential key when injecting a secret
 * @param sink      declared sink
 * @param literal   literal value when not a secret ref
 * @author Gensokyo
 * @since 2026-09-14
 */
public record NamedBinding(
        String name,
        Optional<String> nameLower,
        Optional<String> secretRef,
        Optional<SecretSink> sink,
        Optional<Object> literal,
        Map<String, Object> extras
) {
}
