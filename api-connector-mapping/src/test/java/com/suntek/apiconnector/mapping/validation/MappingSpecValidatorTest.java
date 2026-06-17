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
}
