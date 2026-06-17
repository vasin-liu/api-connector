/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.mapping.spi.MappingEngine;

/**
 * Dispatches declarative rules per direction; Groovy scripts wired in Plan 04 (D-10).
 */
public final class MappingEngineImpl implements MappingEngine {

    private final DeclarativeRuleExecutor ruleExecutor;

    public MappingEngineImpl(DeclarativeRuleExecutor ruleExecutor) {
        this.ruleExecutor = ruleExecutor;
    }

    @Override
    public String mapRequest(MappingContext ctx, ResolvedMapping config) {
        return mapDirection(ctx, config != null ? config.request() : null);
    }

    @Override
    public String mapResponse(MappingContext ctx, ResolvedMapping config) {
        return mapDirection(ctx, config != null ? config.response() : null);
    }

    @Override
    public String mapError(MappingContext ctx, ResolvedMapping config) {
        return mapDirection(ctx, config != null ? config.error() : null);
    }

    private String mapDirection(MappingContext ctx, ResolvedMapping.ResolvedDirection direction) {
        if (direction == null || !direction.isConfigured()) {
            return ctx.rawBody();
        }
        if (direction.hasRules()) {
            return ruleExecutor.applyRules(ctx.rawBody(), direction.rules());
        }
        if (direction.hasScript()) {
            throw new UnsupportedOperationException("Groovy mapping scripts are not yet implemented");
        }
        return ctx.rawBody();
    }
}
