/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.plan.SecretSink;

import java.util.Objects;

/**
 * Sink + destination checks. Policy may only deny; it never upgrades a sink.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class SecretSinkPolicy {

    /**
     * @param secret      secret being applied
     * @param sink        declared sink
     * @param destination target api
     */
    public void check(SecretValue secret, SecretSink sink, SinkDestination destination) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(destination, "destination");
        String owner = secret.metadata() == null ? "" : secret.metadata().apiId();
        String target = destination.apiId() == null ? "" : destination.apiId();
        if (!owner.equals(target)) {
            throw new ExecuteException(
                    ExecuteException.SECRET_SINK_DENIED,
                    "secret destination " + target + " does not match api " + owner
            );
        }
    }
}
