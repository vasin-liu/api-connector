/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.DeclarativeRuleExecutor;
import com.suntek.apiconnector.mapping.ErrorMappingTrigger;
import com.suntek.apiconnector.mapping.GroovyMappingScriptProvider;
import com.suntek.apiconnector.mapping.MappingEngineImpl;
import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingConfigResolverTest {

    @Test
    void connectorMappingOnlyResolvesConnectorRules() {
        MappingRule rename = renameRule("$.clientId", "$.app_id");
        ConnectorSpec spec = connectorSpec(new MappingSpec(
                new DirectionMappingSpec(List.of(rename), null),
                null,
                null));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertTrue(resolved.hasRequest());
        assertFalse(resolved.hasResponse());
        assertFalse(resolved.hasError());
        assertEquals(rename, resolved.request().rules().getFirst());
    }

    @Test
    void endpointOverrideReplacesRequestOnly() {
        MappingRule connectorRename = renameRule("$.clientId", "$.app_id");
        MappingRule overrideRename = renameRule("$.vendorId", "$.id");
        MappingRule responseRename = renameRule("$.result", "$.data");
        ConnectorSpec spec = connectorSpec(
                new MappingSpec(
                        new DirectionMappingSpec(List.of(connectorRename), null),
                        new DirectionMappingSpec(List.of(responseRename), null),
                        null),
                new MappingSpec(
                        new DirectionMappingSpec(List.of(overrideRename), null),
                        null,
                        null));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertEquals(overrideRename, resolved.request().rules().getFirst());
        assertEquals(responseRename, resolved.response().rules().getFirst());
        assertNull(resolved.error());
    }

    @Test
    void noMappingBlockHasAnyMappingFalse() {
        ConnectorSpec spec = connectorSpec(null);

        assertFalse(MappingConfigResolver.hasAnyMapping(spec, spec.endpoints().getFirst()));
        assertFalse(MappingConfigResolver.hasAnyMapping(ResolvedMapping.empty()));
    }

    @Test
    void endpointResponseScriptOverrideHasAnyMappingTrue() {
        ConnectorSpec spec = connectorSpec(
                null,
                new MappingSpec(
                        null,
                        new DirectionMappingSpec(null, "return [:]"),
                        null));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertFalse(resolved.hasRequest());
        assertTrue(resolved.hasResponse());
        assertTrue(MappingConfigResolver.hasAnyMapping(resolved));
        assertNotNull(resolved.response().script());
    }

    @Test
    void connectorErrorMappingUsedWhenEndpointHasNoErrorOverride() {
        MappingRule connectorErrorRename = renameRule("$.errCode", "$.code");
        ConnectorSpec spec = connectorSpec(new MappingSpec(
                null,
                null,
                new DirectionMappingSpec(List.of(connectorErrorRename), null)));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertTrue(resolved.hasError());
        assertEquals(connectorErrorRename, resolved.error().rules().getFirst());
    }

    @Test
    void endpointErrorOverrideReplacesConnectorErrorRules() {
        MappingRule connectorError = renameRule("$.errCode", "$.code");
        MappingRule endpointError = renameRule("$.vendorCode", "$.code");
        ConnectorSpec spec = connectorSpec(
                new MappingSpec(
                        null,
                        null,
                        new DirectionMappingSpec(List.of(connectorError), null)),
                new MappingSpec(
                        null,
                        null,
                        new DirectionMappingSpec(List.of(endpointError), null)));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertEquals(endpointError, resolved.error().rules().getFirst());
    }

    @Test
    void endpointRequestScriptOverrideDoesNotClearConnectorErrorRules() {
        MappingRule connectorError = renameRule("$.errCode", "$.code");
        ConnectorSpec spec = connectorSpec(
                new MappingSpec(
                        null,
                        null,
                        new DirectionMappingSpec(List.of(connectorError), null)),
                new MappingSpec(
                        new DirectionMappingSpec(null, "return [:]"),
                        null,
                        null));
        EndpointSpec endpoint = spec.endpoints().getFirst();

        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        assertTrue(resolved.hasRequest());
        assertTrue(resolved.hasError());
        assertEquals(connectorError, resolved.error().rules().getFirst());
    }

    @Test
    void resolveAndMapErrorProducesEndpointSpecificErrorTemplate() throws Exception {
        MappingRule connectorError = renameRule("$.errCode", "$.code");
        MappingRule endpointError = renameRule("$.vendorCode", "$.code");
        ConnectorSpec spec = connectorSpec(
                new MappingSpec(
                        null,
                        null,
                        new DirectionMappingSpec(List.of(connectorError), null)),
                new MappingSpec(
                        null,
                        null,
                        new DirectionMappingSpec(List.of(endpointError), null)));
        EndpointSpec endpoint = spec.endpoints().getFirst();
        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);

        MappingEngine engine = new MappingEngineImpl(
                new DeclarativeRuleExecutor(),
                new GroovyMappingScriptProvider(new ScriptCompileService()));
        MappingContext ctx = new MappingContext(
                "TEST",
                MappingDirection.ERROR,
                "{\"vendorCode\":\"EP99\",\"errCode\":\"ignored\"}",
                new AuthContextSnapshot("TEST", List.of("none"), Map.of(), Map.of()),
                new EndpointMeta(endpoint.id(), endpoint.method(), endpoint.path()));

        String output = engine.mapError(ctx, resolved, new ErrorMappingTrigger(500, false));

        assertEquals("EP99", com.jayway.jsonpath.JsonPath.read(output, "$.code"));
    }

    @Test
    void transformOnlySpecHasAnyMappingFalse() {
        ConnectorSpec spec = new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec(
                        "ep1",
                        "GET",
                        "/ep1",
                        null,
                        true,
                        null,
                        null,
                        null)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null,
                List.of(Map.of("type", "sm4_encrypt")));

        assertFalse(MappingConfigResolver.hasAnyMapping(spec, spec.endpoints().getFirst()));
    }

    @Test
    void emptyMappingObjectHasAnyMappingFalse() {
        ConnectorSpec spec = connectorSpec(new MappingSpec(null, null, null));

        assertFalse(MappingConfigResolver.hasAnyMapping(spec, spec.endpoints().getFirst()));
        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, spec.endpoints().getFirst());
        assertFalse(resolved.hasRequest());
        assertFalse(resolved.hasError());
    }

    private static ConnectorSpec connectorSpec(MappingSpec mapping) {
        return connectorSpec(mapping, null);
    }

    private static ConnectorSpec connectorSpec(MappingSpec mapping, MappingSpec endpointOverride) {
        return new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(new EndpointSpec(
                        "ep1",
                        "GET",
                        "/ep1",
                        null,
                        true,
                        null,
                        null,
                        endpointOverride)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                mapping,
                null);
    }

    private static MappingRule renameRule(String source, String target) {
        return new MappingRule("rename", source, target, null, null, null);
    }
}
