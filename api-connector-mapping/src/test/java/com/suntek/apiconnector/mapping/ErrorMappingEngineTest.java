/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.model.MappingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAP-04: vendor error JSON → {@code LegacySuntekResult} field shape (code, message, success:false).
 */
class ErrorMappingEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MappingEngine engine = new MappingEngineImpl(
            new DeclarativeRuleExecutor(),
            new GroovyMappingScriptProvider(new ScriptCompileService()));

    @Test
    void shouldMapErrorTrueForHttp4xxAnd5xx() {
        assertTrue(ErrorMappingTrigger.shouldMapError(new ErrorMappingTrigger(400, true)));
        assertTrue(ErrorMappingTrigger.shouldMapError(new ErrorMappingTrigger(500, true)));
    }

    @Test
    void shouldMapErrorTrueForBusinessFailureOnHttp200() {
        assertTrue(ErrorMappingTrigger.shouldMapError(new ErrorMappingTrigger(200, false)));
    }

    @Test
    void shouldMapErrorFalseForHttp200BusinessSuccess() {
        assertFalse(ErrorMappingTrigger.shouldMapError(new ErrorMappingTrigger(200, true)));
    }

    @Test
    void mapsVendorErrorToLegacySuntekResultShape() throws Exception {
        String vendorBody = "{\"errCode\":\"500\",\"errMsg\":\"vendor fail\"}";
        ResolvedMapping config = ResolvedMapping.of(
                null,
                null,
                ResolvedMapping.ResolvedDirection.ofRules(List.of(
                        renameRule("$.errCode", "$.code"),
                        renameRule("$.errMsg", "$.message"),
                        setRule("$.success", false))));
        MappingContext ctx = errorContext(vendorBody);

        String output = engine.mapError(ctx, config, new ErrorMappingTrigger(500, false));

        JsonNode legacy = MAPPER.readTree(output);
        assertEquals("500", legacy.get("code").asText());
        assertEquals("vendor fail", legacy.get("message").asText());
        assertFalse(legacy.get("success").asBoolean());
    }

    @Test
    void appliesErrorMappingOnHttp200BusinessFailure() throws Exception {
        String vendorBody = "{\"errCode\":\"E01\",\"errMsg\":\"biz fail\"}";
        ResolvedMapping config = ResolvedMapping.of(
                null,
                null,
                ResolvedMapping.ResolvedDirection.ofRules(List.of(
                        renameRule("$.errCode", "$.code"),
                        renameRule("$.errMsg", "$.message"),
                        setRule("$.success", false))));

        String output = engine.mapError(
                errorContext(vendorBody),
                config,
                new ErrorMappingTrigger(200, false));

        JsonNode legacy = MAPPER.readTree(output);
        assertEquals("E01", legacy.get("code").asText());
        assertEquals("biz fail", legacy.get("message").asText());
        assertFalse(legacy.get("success").asBoolean());
    }

    @Test
    void returnsPassthroughWhenTriggerIndicatesSuccess() {
        String vendorBody = "{\"errCode\":\"500\",\"errMsg\":\"ignored\"}";
        ResolvedMapping config = ResolvedMapping.of(
                null,
                null,
                ResolvedMapping.ResolvedDirection.ofRules(List.of(
                        renameRule("$.errCode", "$.code"))));

        String output = engine.mapError(
                errorContext(vendorBody),
                config,
                new ErrorMappingTrigger(200, true));

        assertEquals(vendorBody, output);
    }

    @Test
    void passthroughVendorBodyWhenNoErrorMappingConfigured() {
        String vendorBody = "{\"errCode\":\"500\",\"errMsg\":\"vendor fail\"}";
        MappingContext ctx = errorContext(vendorBody);

        String output = engine.mapError(
                ctx,
                ResolvedMapping.empty(),
                new ErrorMappingTrigger(500, false));

        assertEquals(vendorBody, output);
    }

    @Test
    void groovyErrorScriptMapsToLegacyShape() throws Exception {
        ResolvedMapping config = ResolvedMapping.of(
                null,
                null,
                ResolvedMapping.ResolvedDirection.ofScript(
                        """
                                def m = ctx.bodyAsMap()
                                [code: m.vendorCode, message: m.vendorMessage, success: false]
                                """,
                        null));

        String output = engine.mapError(
                errorContext("{\"vendorCode\":\"99\",\"vendorMessage\":\"script fail\"}"),
                config,
                new ErrorMappingTrigger(200, false));

        JsonNode legacy = MAPPER.readTree(output);
        assertEquals("99", legacy.get("code").asText());
        assertEquals("script fail", legacy.get("message").asText());
        assertFalse(legacy.get("success").asBoolean());
    }

    private static MappingContext errorContext(String body) {
        AuthContextSnapshot snapshot = new AuthContextSnapshot(
                "TEST",
                List.of("none"),
                Map.of(),
                Map.of());
        return new MappingContext(
                "TEST",
                MappingDirection.ERROR,
                body,
                snapshot,
                new EndpointMeta("ep1", "GET", "/ep1"));
    }

    private static MappingRule renameRule(String source, String target) {
        return new MappingRule("rename", source, target, null, null, null);
    }

    private static MappingRule setRule(String target, Object value) {
        return new MappingRule("set", null, target, null, value, null);
    }
}
