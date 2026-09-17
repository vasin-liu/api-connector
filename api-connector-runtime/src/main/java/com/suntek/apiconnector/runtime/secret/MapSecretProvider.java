/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory secret map for tests and execute-time seeding of refs.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class MapSecretProvider implements SecretProvider {

    private final ConcurrentHashMap<String, byte[]> values = new ConcurrentHashMap<>();

    /**
     * @param secretRef provider reference
     * @param utf8      UTF-8 material
     */
    public void put(String secretRef, String utf8) {
        put(secretRef, utf8.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param secretRef provider reference
     * @param material  secret bytes (copied)
     */
    public void put(String secretRef, byte[] material) {
        values.put(secretRef, material.clone());
    }

    /**
     * Puts the ref string as material when absent (Phase 0 fixture default).
     *
     * @param secretRef provider reference
     */
    public void putRefAsMaterialIfAbsent(String secretRef) {
        values.putIfAbsent(secretRef, secretRef.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> get(String secretRef) {
        byte[] found = values.get(secretRef);
        return found == null ? Optional.empty() : Optional.of(found.clone());
    }
}
