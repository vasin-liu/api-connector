/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.validation;

import com.suntek.apiconnector.spec.ConnectorSpecParser;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingSpecValidatorTest {

    private final MappingSpecValidator validator = new MappingSpecValidator();

    @Test
    void validDemoRenameSpecPasses() {
        ConnectorSpec spec = loadFixture("mapping/demo-rename.yaml");

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @Test
    void rejectsInvalidJsonPath() {
        ConnectorSpec spec = connectorWithRequestRule(renameRule("$.invalid[[", "$.target"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("mapping.request.rules[0].source"));
        assertTrue(ex.getMessage().contains("invalid JSONPath"));
    }

    @Test
    void rejectsUnknownOp() {
        ConnectorSpec spec = connectorWithRequestRule(new MappingRule("flurp", "$.a", "$.b", null, null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("mapping.request.rules[0].op"));
        assertTrue(ex.getMessage().contains("flurp"));
    }

    @Test
    void rejectsCoerceWithoutType() {
        ConnectorSpec spec = connectorWithRequestRule(
                new MappingRule("coerce", "$.count", "$.countNum", null, null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("mapping.request.rules[0].type"));
        assertTrue(ex.getMessage().contains("coerce"));
    }

    @Test
    void acceptsValidSm4EncryptTransform() {
        ConnectorSpec spec = connectorWithTransform(List.of(
                Map.of("type", "sm4_encrypt", "direction", "request", "keyRef", "appSecret")));

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @Test
    void rejectsUnknownTransformType() {
        ConnectorSpec spec = connectorWithTransform(List.of(Map.of("type", "rot13_encrypt")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("transform[0].type"));
        assertTrue(ex.getMessage().contains("rot13_encrypt"));
    }

    @Test
    void rejectsTransformMissingType() {
        ConnectorSpec spec = connectorWithTransform(List.of(Map.of("keyRef", "appSecret")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("transform[0].type is required"));
    }

    @Test
    void rejectsSm4EncryptWithoutKeyRef() {
        ConnectorSpec spec = connectorWithTransform(List.of(Map.of("type", "sm4_encrypt", "direction", "request")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("transform[0].keyRef is required"));
    }

    @Test
    void rejectsSm4EncryptWithInlineKey() {
        ConnectorSpec spec = connectorWithTransform(List.of(
                Map.of("type", "sm4_encrypt", "keyRef", "appSecret", "key", "1234567890abcdef")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("transform[0].key inline secret is forbidden"));
    }

    @Test
    void rejectsSm4DecryptWithInlineKey() {
        ConnectorSpec spec = connectorWithTransform(List.of(
                Map.of("type", "sm4_decrypt", "keyRef", "appSecret", "key", "1234567890abcdef")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("transform[0].key inline secret is forbidden"));
        assertTrue(ex.getMessage().contains("sm4_decrypt"));
    }

    @Test
    void rejectsBusinessEnvelopeEnabled() {
        ConnectorSpec spec = connectorWithTransform(List.of(
                Map.of("type", "business_envelope", "enabled", true)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(spec));
        assertTrue(ex.getMessage().contains("business_envelope"));
        assertTrue(ex.getMessage().contains("unsupported"));
    }

    @Test
    void acceptsBusinessEnvelopeDisabled() {
        ConnectorSpec spec = connectorWithTransform(List.of(
                Map.of("type", "business_envelope", "enabled", false)));

        assertDoesNotThrow(() -> validator.validate(spec));
    }

    @SuppressWarnings("unchecked")
    private static ConnectorSpec loadFixture(String resourcePath) {
        InputStream in = MappingSpecValidatorTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing fixture: " + resourcePath);
        }
        Map<String, Object> root = new Yaml().load(in);
        return ConnectorSpecParser.parse(root);
    }

    private static ConnectorSpec connectorWithRequestRule(MappingRule rule) {
        MappingSpec mapping = new MappingSpec(
                new DirectionMappingSpec(List.of(rule), null),
                null,
                null);
        return new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(),
                null,
                null,
                mapping,
                null);
    }

    private static MappingRule renameRule(String source, String target) {
        return new MappingRule("rename", source, target, null, null, null);
    }

    private static ConnectorSpec connectorWithTransform(List<Map<String, Object>> transform) {
        return new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://example.com",
                "HTTP",
                Map.of("type", "none"),
                List.of(),
                null,
                null,
                null,
                transform);
    }
}
