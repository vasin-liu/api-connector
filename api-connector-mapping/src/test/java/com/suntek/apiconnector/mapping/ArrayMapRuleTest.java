/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.spec.model.MappingRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayMapRuleTest {

    private final DeclarativeRuleExecutor executor = new DeclarativeRuleExecutor();

    @Test
    void arrayMapRenamesIdPerElement() {
        String input = "{\"items\":[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]}";
        List<MappingRule> rules = List.of(arrayMapRule(
                "$.items",
                "$.items",
                List.of(renameRule("$.id", "$.itemId"))));

        String output = executor.applyRules(input, rules);

        assertEquals(1, ((Number) JsonPath.read(output, "$.items[0].itemId")).intValue());
        assertEquals(2, ((Number) JsonPath.read(output, "$.items[1].itemId")).intValue());
        assertEquals("a", JsonPath.read(output, "$.items[0].name"));
        assertEquals("b", JsonPath.read(output, "$.items[1].name"));
        assertTrue(!output.contains("\"id\""));
    }

    @Test
    void arrayMapMapsNestedObjectInsideElement() {
        String input = "{\"rows\":[{\"detail\":{\"code\":\"x\",\"qty\":3}}]}";
        List<MappingRule> rules = List.of(arrayMapRule(
                "$.rows",
                "$.rows",
                List.of(renameRule("$.detail.code", "$.detail.sku"))));

        String output = executor.applyRules(input, rules);

        assertEquals("x", JsonPath.read(output, "$.rows[0].detail.sku"));
        assertEquals(3, ((Number) JsonPath.read(output, "$.rows[0].detail.qty")).intValue());
        assertTrue(!output.contains("\"code\""));
    }

    @Test
    void emptyArrayPassesThrough() {
        String input = "{\"items\":[]}";
        List<MappingRule> rules = List.of(arrayMapRule(
                "$.items",
                "$.items",
                List.of(renameRule("$.id", "$.itemId"))));

        String output = executor.applyRules(input, rules);

        Object items = JsonPath.read(output, "$.items");
        assertInstanceOf(List.class, items);
        assertTrue(((List<?>) items).isEmpty());
    }

    private static MappingRule arrayMapRule(String source, String target, List<MappingRule> nestedRules) {
        return new MappingRule("array_map", source, target, null, null, nestedRules);
    }

    private static MappingRule renameRule(String source, String target) {
        return new MappingRule("rename", source, target, null, null, null);
    }
}
