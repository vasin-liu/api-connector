/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.mapping.ResolvedMapping;
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
