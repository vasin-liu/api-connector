/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.spi;

import com.suntek.apiconnector.mapping.TransformContext;

/**
 * Body transform pipeline SPI — crypto/envelope steps applied separately from JSON {@code mapping.*}
 * blocks (D-20, D-22, ADR-002).
 *
 * <p>Each step transforms a body {@code String} in to a body {@code String} out (D-22). Steps are
 * resolved by {@link #type()} from {@code ConnectorSpec.transform[]} and executed in declared order
 * by direction via the transform pipeline.</p>
 */
public interface TransformStep {

    /**
     * Stable transform type discriminator (e.g. {@code sm4_encrypt}) matching {@code transform[].type}.
     *
     * @return transform type key
     */
    String type();

    /**
     * Transforms the body carried by {@code ctx}, returning the new body.
     *
     * @param ctx transform invocation context (body, step config, credentials, direction)
     * @return transformed body
     */
    String apply(TransformContext ctx);
}
