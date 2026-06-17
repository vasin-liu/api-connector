/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Documents Phase 3 orchestrator passthrough contract (D-26, MAP-07).
 *
 * <p>{@code DefaultIntegrationOrchestrator} will short-circuit mapping when
 * {@link MappingConfigResolver#hasAnyMapping(ResolvedMapping)} is false.
 * Passthrough skips JSON mapping only; {@code transform[]} still runs when configured (D-25).</p>
 */
class PassthroughMappingGuardTest {

    @Test
    void orchestratorGuardSkipsMappingEngineWhenNoMappingConfigured() {
        ConnectorSpec spec = passthroughSpec();
        EndpointSpec endpoint = spec.endpoints().getFirst();
        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);
        RecordingMappingEngine engine = new RecordingMappingEngine();

        String body = "{\"payload\":true}";
        if (MappingConfigResolver.hasAnyMapping(resolved)) {
            body = engine.mapRequest(mappingContext(body), resolved);
        }

        assertFalse(MappingConfigResolver.hasAnyMapping(resolved));
        assertEquals(0, engine.invocationCount());
        assertEquals("{\"payload\":true}", body);
    }

    @Test
    void orchestratorGuardInvokesMappingEngineWhenMappingConfigured() {
        ConnectorSpec spec = mappedSpec();
        EndpointSpec endpoint = spec.endpoints().getFirst();
        ResolvedMapping resolved = MappingConfigResolver.resolve(spec, endpoint);
        RecordingMappingEngine engine = new RecordingMappingEngine();

        String body = "{\"clientId\":\"x\"}";
        if (MappingConfigResolver.hasAnyMapping(resolved)) {
            body = engine.mapRequest(mappingContext(body), resolved);
        }

        assertEquals(1, engine.invocationCount());
        assertEquals("mapped", body);
    }

    private static ConnectorSpec passthroughSpec() {
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
                        null)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null,
                null);
    }

    private static ConnectorSpec mappedSpec() {
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
                        null)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                new com.suntek.apiconnector.spec.model.MappingSpec(
                        new com.suntek.apiconnector.spec.model.DirectionMappingSpec(
                                List.of(new com.suntek.apiconnector.spec.model.MappingRule(
                                        "rename", "$.clientId", "$.app_id", null, null, null)),
                                null),
                        null,
                        null),
                null);
    }

    private static MappingContext mappingContext(String body) {
        return new MappingContext(
                "TEST",
                MappingDirection.REQUEST,
                body,
                new AuthContextSnapshot("TEST", List.of("none"), Map.of(), Map.of()),
                new EndpointMeta("ep1", "GET", "/ep1"));
    }

    /**
     * Test double documenting Mockito-style verify-never for Phase 3 wiring.
     */
    private static final class RecordingMappingEngine implements MappingEngine {

        private int invocationCount;

        @Override
        public String mapRequest(MappingContext ctx, ResolvedMapping config) {
            invocationCount++;
            return "mapped";
        }

        @Override
        public String mapResponse(MappingContext ctx, ResolvedMapping config) {
            invocationCount++;
            return "mapped";
        }

        @Override
        public String mapError(
                MappingContext ctx,
                ResolvedMapping config,
                com.suntek.apiconnector.mapping.ErrorMappingTrigger trigger) {
            invocationCount++;
            return "mapped";
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
