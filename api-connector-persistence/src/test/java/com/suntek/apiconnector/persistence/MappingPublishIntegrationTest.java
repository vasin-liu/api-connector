/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.engine.ConnectorPublishListener;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.engine.MappingConfigResolver;
import com.suntek.apiconnector.mapping.DeclarativeRuleExecutor;
import com.suntek.apiconnector.mapping.GroovyMappingScriptProvider;
import com.suntek.apiconnector.mapping.MappingEngineImpl;
import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.mapping.TransformPipeline;
import com.suntek.apiconnector.mapping.TransformStepRegistry;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.mapping.transform.Sm4DecryptTransformStep;
import com.suntek.apiconnector.mapping.transform.Sm4EncryptTransformStep;
import com.suntek.apiconnector.mapping.transform.StubTransformStep;
import com.suntek.apiconnector.persistence.jdbc.JdbcConnectorConfigStore;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import com.suntek.apiconnector.spec.ConnectorSpecParser;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MAP-05: JDBC SPEC_JSON persist + publish reload proves mapping rules apply without restart
 * (D-27, D-28, D-29). Wires the persistence + engine + mapping stack directly over an embedded H2.
 */
class MappingPublishIntegrationTest {

    private static final String CODE_3RD = "MAP_PUBLISH_IT";
    private static final String SM4_KEY = "1234567890abcdef";

    private EmbeddedDatabase database;
    private JdbcConnectorConfigStore store;
    private ConnectorRegistry connectorRegistry;
    private ConnectorConfigSyncService syncService;
    private MappingEngine mappingEngine;
    private TransformPipeline transformPipeline;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/schema.sql")
                .build();
        jdbc = new JdbcTemplate(database);
        store = new JdbcConnectorConfigStore(jdbc, objectMapper);

        ScriptCompileService scriptCompileService = new ScriptCompileService();
        TransformStepRegistry transformRegistry = new TransformStepRegistry(List.of(
                new Sm4EncryptTransformStep(),
                new Sm4DecryptTransformStep(),
                StubTransformStep.businessEnvelope()));
        ConnectorPublishListener publishListener =
                new ConnectorPublishListener(scriptCompileService, new TokenCache(), transformRegistry);
        connectorRegistry = new ConnectorRegistry(publishListener);
        syncService = new ConnectorConfigSyncService(
                connectorRegistry, store, new IntegrationPersistenceProperties());
        mappingEngine = new MappingEngineImpl(
                new DeclarativeRuleExecutor(), new GroovyMappingScriptProvider(scriptCompileService));
        transformPipeline = new TransformPipeline(transformRegistry);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void mappingRulesSurviveJdbcPublishAndApplyAfterReloadWithoutRestart() {
        ConnectorSpec spec = connectorWithMappingAndTransform();
        store.save(spec, Map.of("appId", "demo", "appSecret", SM4_KEY), ConnectorSpecStatus.PUBLISHED);

        // (2) reloadFromStore → registry.save → publish listener validates + republishes (D-29).
        int reloaded = syncService.reloadFromStore();
        assertTrue(reloaded >= 1, "expected at least one published connector reloaded");

        // (3) Mapping rules resolvable from registry without restart (MAP-05, ROADMAP SC#4).
        ConnectorSpec runtime = connectorRegistry.require(CODE_3RD);
        ResolvedMapping resolved = MappingConfigResolver.resolve(runtime, null);
        assertTrue(MappingConfigResolver.hasAnyMapping(resolved));
        assertTrue(resolved.hasRequest());

        // (4) Apply mapping engine on a sample body — rename + set produce mapped output.
        MappingContext ctx = new MappingContext(
                CODE_3RD,
                MappingDirection.REQUEST,
                "{\"clientId\":\"abc\",\"keep\":1}",
                new AuthContextSnapshot(CODE_3RD, List.of(), Map.of(), Map.of()),
                new EndpointMeta("echo", "POST", "/api/echo"));
        String mapped = mappingEngine.mapRequest(ctx, resolved);
        assertTrue(mapped.contains("app_id"), "renamed field present: " + mapped);
        assertFalse(mapped.contains("clientId"), "original field removed: " + mapped);
        assertTrue(mapped.contains("\"1.0\""), "set version present: " + mapped);

        // transform[] still runs (D-25): SM4 round-trips with the reloaded credentials.
        Map<String, String> credentials = connectorRegistry.credentials(CODE_3RD);
        String encrypted = transformPipeline.applyRequest(mapped, runtime.transform(), credentials);
        assertFalse(encrypted.contains("app_id"), "body encrypted: " + encrypted);
        String decrypted = transformPipeline.applyResponse(encrypted, runtime.transform(), credentials);
        assertEquals(mapped, decrypted);
    }

    @Test
    void specJsonRoundTripPreservesMappingBlock() {
        ConnectorSpec spec = connectorWithMappingAndTransform();
        store.save(spec, Map.of("appId", "demo", "appSecret", SM4_KEY), ConnectorSpecStatus.PUBLISHED);

        // (5) Load raw SPEC_JSON from store and parse — mapping block intact (D-28).
        String specJson = jdbc.queryForObject(
                "SELECT s.SPEC_JSON FROM IT_CONNECTOR_SPEC s "
                        + "JOIN IT_CONNECTOR_CLIENT c ON c.ID = s.CLIENT_ID WHERE c.CODE3RD = ?",
                String.class,
                CODE_3RD);
        assertNotNull(specJson);

        ConnectorSpec parsed = parse(specJson);
        MappingSpec mapping = parsed.mapping();
        assertNotNull(mapping, "mapping block survived SPEC_JSON round-trip");
        assertNotNull(mapping.request());
        List<MappingRule> rules = mapping.request().rules();
        assertNotNull(rules);
        assertEquals("rename", rules.get(0).op());
        assertEquals("$.clientId", rules.get(0).source());
        assertEquals("$.app_id", rules.get(0).target());

        // transform[] also persisted in SPEC_JSON.
        assertNotNull(parsed.transform());
        assertEquals("sm4_encrypt", String.valueOf(parsed.transform().get(0).get("type")));
    }

    private ConnectorSpec parse(String specJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(
                    specJson, new TypeReference<Map<String, Object>>() {
                    });
            return ConnectorSpecParser.parse(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse stored SPEC_JSON", ex);
        }
    }

    private static ConnectorSpec connectorWithMappingAndTransform() {
        MappingSpec mapping = new MappingSpec(
                new DirectionMappingSpec(
                        List.of(
                                new MappingRule("rename", "$.clientId", "$.app_id", null, null, null),
                                new MappingRule("set", null, "$.version", null, "1.0", null)),
                        null),
                null,
                null);
        List<Map<String, Object>> transform = List.of(
                Map.of("type", "sm4_encrypt", "direction", "request", "keyRef", "appSecret"),
                Map.of("type", "sm4_decrypt", "direction", "response", "keyRef", "appSecret"));
        return new ConnectorSpec(
                CODE_3RD,
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(),
                null,
                null,
                mapping,
                transform);
    }
}
