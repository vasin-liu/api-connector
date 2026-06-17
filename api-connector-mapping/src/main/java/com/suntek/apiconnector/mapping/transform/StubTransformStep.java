/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.transform;

import com.suntek.apiconnector.mapping.TransformContext;
import com.suntek.apiconnector.mapping.exception.MappingExceptions;
import com.suntek.apiconnector.mapping.spi.TransformStep;

/**
 * Stub for deferred transform types — currently {@code business_envelope} (D-20 deferred).
 *
 * <p>Registered so the type is recognized at publish, but execution and an {@code enabled:true}
 * configuration are unsupported in Phase 2: applying throws {@code TRANSFORM_UNSUPPORTED} and the
 * publish validator rejects {@code enabled:true}. Disabled (or absent) steps are a no-op passthrough.</p>
 */
public final class StubTransformStep implements TransformStep {

    public static final String BUSINESS_ENVELOPE = "business_envelope";
    public static final String ENABLED = "enabled";

    private final String type;

    public StubTransformStep(String type) {
        this.type = type;
    }

    /**
     * Convenience factory for the {@code business_envelope} stub.
     *
     * @return business envelope stub step
     */
    public static StubTransformStep businessEnvelope() {
        return new StubTransformStep(BUSINESS_ENVELOPE);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String apply(TransformContext ctx) {
        if (isEnabled(ctx)) {
            throw MappingExceptions.transformUnsupported(type);
        }
        return ctx.body();
    }

    private static boolean isEnabled(TransformContext ctx) {
        Object enabled = ctx.stepConfig().get(ENABLED);
        return enabled != null && Boolean.parseBoolean(String.valueOf(enabled));
    }
}
