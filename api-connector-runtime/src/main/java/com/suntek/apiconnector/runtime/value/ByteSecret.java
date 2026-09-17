/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.value;

import com.suntek.apiconnector.core.value.SecretConsumer;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.core.value.SecretValue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * In-memory secret without a generic reveal.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class ByteSecret implements SecretValue {

    private final SecretMetadata metadata;
    private final byte[] material;

    /**
     * @param metadata non-sensitive description
     * @param material secret bytes (copied)
     */
    public ByteSecret(SecretMetadata metadata, byte[] material) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.material = Arrays.copyOf(Objects.requireNonNull(material, "material"), material.length);
    }

    /**
     * @param metadata non-sensitive description
     * @param utf8     UTF-8 secret text
     * @return secret
     */
    public static ByteSecret utf8(SecretMetadata metadata, String utf8) {
        return new ByteSecret(metadata, utf8.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public SecretMetadata metadata() {
        return metadata;
    }

    @Override
    public void use(SecretConsumer consumer) {
        consumer.accept(Arrays.copyOf(material, material.length));
    }

    @Override
    public String toString() {
        return "SecretValue(" + metadata.secretRef() + ")";
    }
}
