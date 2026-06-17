/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.mapping.spi.MappingEngine;

/**
 * Dispatches declarative rules or Groovy scripts per direction (D-10).
 */
public final class MappingEngineImpl implements MappingEngine {

    private final DeclarativeRuleExecutor ruleExecutor;
    private final GroovyMappingScriptProvider scriptProvider;

    public MappingEngineImpl(DeclarativeRuleExecutor ruleExecutor, GroovyMappingScriptProvider scriptProvider) {
        this.ruleExecutor = ruleExecutor;
        this.scriptProvider = scriptProvider;
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
    public String mapError(MappingContext ctx, ResolvedMapping config, ErrorMappingTrigger trigger) {
        if (config == null || !config.hasError()) {
            return ctx.rawBody();
        }
        if (!ErrorMappingTrigger.shouldMapError(trigger)) {
            return ctx.rawBody();
        }
        return mapDirection(ctx, config.error());
    }

    private String mapDirection(MappingContext ctx, ResolvedMapping.ResolvedDirection direction) {
        if (direction == null || !direction.isConfigured()) {
            return ctx.rawBody();
        }
        if (direction.hasRules()) {
            return ruleExecutor.applyRules(ctx.rawBody(), direction.rules());
        }
        if (direction.hasScript()) {
            String compileLabel = mappingCompileLabel(ctx);
            if (direction.compiledScript() != null) {
                return scriptProvider.evalAsJson(direction.compiledScript(), ctx, compileLabel);
            }
            return scriptProvider.applyAsJson(ctx, direction.script(), compileLabel);
        }
        return ctx.rawBody();
    }

    private static String mappingCompileLabel(MappingContext ctx) {
        String direction = ctx.direction().name().toLowerCase();
        if (ctx.endpoint() != null && ctx.endpoint().id() != null && !ctx.endpoint().id().isBlank()) {
            return ctx.code3rd() + ":endpoint:" + ctx.endpoint().id() + ":mapping:" + direction;
        }
        return ctx.code3rd() + ":mapping:" + direction;
    }
}
