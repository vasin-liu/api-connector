/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.spec.model.MappingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MappingEngineImplTest {

    private final MappingEngine engine = new MappingEngineImpl(new DeclarativeRuleExecutor());

    @Test
    void mapRequestAppliesDeclarativeRules() {
        MappingContext ctx = context(MappingDirection.REQUEST, "{\"clientId\":\"x\"}");
        ResolvedMapping config = ResolvedMapping.of(
                ResolvedMapping.ResolvedDirection.ofRules(
                        List.of(new MappingRule("rename", "$.clientId", "$.app_id", null, null, null))),
                null,
                null);

        String output = engine.mapRequest(ctx, config);

        assertEquals("x", JsonPath.read(output, "$.app_id"));
    }

    @Test
    void mapResponseAppliesDeclarativeRules() {
        MappingContext ctx = context(MappingDirection.RESPONSE, "{\"result\":\"ok\"}");
        ResolvedMapping config = ResolvedMapping.of(
                null,
                ResolvedMapping.ResolvedDirection.ofRules(
                        List.of(new MappingRule("rename", "$.result", "$.data", null, null, null))),
                null);

        String output = engine.mapResponse(ctx, config);

        assertEquals("ok", JsonPath.read(output, "$.data"));
    }

    @Test
    void scriptDirectionThrowsUnsupported() {
        MappingContext ctx = context(MappingDirection.REQUEST, "{}");
        ResolvedMapping config = ResolvedMapping.of(
                ResolvedMapping.ResolvedDirection.ofScript("return [:]", null),
                null,
                null);

        assertThrows(UnsupportedOperationException.class, () -> engine.mapRequest(ctx, config));
    }

    @Test
    void unconfiguredDirectionReturnsRawBody() {
        MappingContext ctx = context(MappingDirection.REQUEST, "{\"keep\":true}");
        ResolvedMapping config = ResolvedMapping.empty();

        String output = engine.mapRequest(ctx, config);

        assertEquals(ctx.rawBody(), output);
    }

    private static MappingContext context(MappingDirection direction, String body) {
        AuthContextSnapshot snapshot = new AuthContextSnapshot(
                "TEST",
                List.of("none"),
                Map.of(),
                Map.of());
        return new MappingContext(
                "TEST",
                direction,
                body,
                snapshot,
                new EndpointMeta("echo", "GET", "/echo"));
    }
}
