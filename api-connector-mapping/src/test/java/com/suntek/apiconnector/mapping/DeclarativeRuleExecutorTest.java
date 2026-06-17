/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.mapping.exception.MappingErrorCode;
import com.suntek.apiconnector.mapping.exception.MappingException;
import com.suntek.apiconnector.spec.ConnectorSpecParser;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeclarativeRuleExecutorTest {

    private final DeclarativeRuleExecutor executor = new DeclarativeRuleExecutor();

    @Test
    void renameMapsField() {
        String input = "{\"clientId\":\"x\"}";
        List<MappingRule> rules = List.of(renameRule("$.clientId", "$.app_id"));

        String output = executor.applyRules(input, rules);

        assertEquals("x", JsonPath.read(output, "$.app_id"));
        assertTrue(output.contains("\"app_id\""));
        assertTrue(!output.contains("clientId"));
    }

    @Test
    void renameFromDemoFixture() {
        ConnectorSpec spec = loadFixture("mapping/demo-rename.yaml");
        List<MappingRule> rules = spec.mapping().request().rules();
        String input = "{\"clientId\":\"x\"}";

        String output = executor.applyRules(input, rules);

        assertEquals("x", JsonPath.read(output, "$.app_id"));
    }

    @Test
    void setWritesLiteral() {
        String input = "{}";
        List<MappingRule> rules = List.of(setRule("$.version", "1.0"));

        String output = executor.applyRules(input, rules);

        assertEquals("1.0", JsonPath.read(output, "$.version"));
    }

    @Test
    void coerceStringToNumber() {
        String input = "{\"count\":\"42\"}";
        List<MappingRule> rules = List.of(coerceRule("$.count", "$.count", "number"));

        String output = executor.applyRules(input, rules);

        Object count = JsonPath.read(output, "$.count");
        assertInstanceOf(Number.class, count);
        assertEquals(42L, ((Number) count).longValue());
    }

    @Test
    void coerceFailureThrows() {
        String input = "{\"count\":\"not-a-number\"}";
        List<MappingRule> rules = List.of(coerceRule("$.count", "$.count", "number"));

        MappingException ex = assertThrows(MappingException.class, () -> executor.applyRules(input, rules));

        assertEquals(MappingErrorCode.MAPPING_COERCE_FAILED, ex.code());
        assertEquals("number", ex.details().get("expectedType"));
    }

    @Test
    void nestCreatesParentPath() {
        String input = "{\"value\":\"nested\"}";
        List<MappingRule> rules = List.of(nestRule("$.value", "$.a.b.c"));

        String output = executor.applyRules(input, rules);

        assertEquals("nested", JsonPath.read(output, "$.a.b.c"));
    }

    @Test
    void missingSourceIsLenient() {
        String input = "{\"keep\":\"yes\"}";
        List<MappingRule> rules = List.of(renameRule("$.missing", "$.target"));

        String output = executor.applyRules(input, rules);

        assertEquals("yes", JsonPath.read(output, "$.keep"));
        assertThrows(Exception.class, () -> JsonPath.read(output, "$.target"));
    }

    @Test
    void sequentialRulesLaterWins() {
        String input = "{}";
        List<MappingRule> rules = List.of(
                setRule("$.flag", "first"),
                setRule("$.flag", "second"));

        String output = executor.applyRules(input, rules);

        assertEquals("second", JsonPath.read(output, "$.flag"));
    }

    @SuppressWarnings("unchecked")
    private static ConnectorSpec loadFixture(String resourcePath) {
        InputStream in = DeclarativeRuleExecutorTest.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing fixture: " + resourcePath);
        }
        Map<String, Object> root = new Yaml().load(in);
        return ConnectorSpecParser.parse(root);
    }

    private static MappingRule renameRule(String source, String target) {
        return new MappingRule("rename", source, target, null, null, null);
    }

    private static MappingRule setRule(String target, Object value) {
        return new MappingRule("set", null, target, null, value, null);
    }

    private static MappingRule coerceRule(String source, String target, String type) {
        return new MappingRule("coerce", source, target, type, null, null);
    }

    private static MappingRule nestRule(String source, String target) {
        return new MappingRule("nest", source, target, null, null, null);
    }
}
