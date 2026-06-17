/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorSpecParserMappingTest {

    @Test
    void parsesMappingRequestRenameRuleFromFixture() {
        ConnectorSpec spec = parseFixture("mapping/demo-rename.yaml");

        assertThat(spec.mapping()).isNotNull();
        assertThat(spec.mapping().request()).isNotNull();
        assertThat(spec.mapping().request().rules()).hasSize(1);
        MappingRule rule = spec.mapping().request().rules().getFirst();
        assertThat(rule.op()).isEqualTo("rename");
        assertThat(rule.source()).isEqualTo("$.clientId");
        assertThat(rule.target()).isEqualTo("$.app_id");
    }

    @Test
    void roundTripPreservesMappingBlock() {
        ConnectorSpec original = parseFixture("mapping/demo-rename.yaml");

        Map<String, Object> serialized = ConnectorSpecParser.toConnectorMap(original);
        ConnectorSpec roundTripped = ConnectorSpecParser.parse(serialized);

        assertThat(roundTripped.mapping()).isNotNull();
        assertThat(roundTripped.mapping().request().rules()).hasSize(1);
        MappingRule rule = roundTripped.mapping().request().rules().getFirst();
        assertThat(rule.op()).isEqualTo("rename");
        assertThat(rule.source()).isEqualTo("$.clientId");
        assertThat(rule.target()).isEqualTo("$.app_id");
        assertThat(roundTripped.code3rd()).isEqualTo(original.code3rd());
    }

    @Test
    void rejectsDirectionWithBothRulesAndScript() {
        assertThatThrownBy(() -> ConnectorSpecParser.parse(Map.of(
                "code3rd", "BAD",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "mapping", Map.of(
                        "request", Map.of(
                                "rules", List.of(Map.of("op", "rename", "source", "$.a", "target", "$.b")),
                                "script", "return [:]")),
                "endpoints", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapping.request")
                .hasMessageContaining("rules and script");
    }

    @Test
    void endpointMappingOverrideStoredOnModel() {
        ConnectorSpec spec = ConnectorSpecParser.parse(Map.of(
                "code3rd", "OVERRIDE",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "mapping", Map.of(
                        "request", Map.of(
                                "rules", List.of(Map.of(
                                        "op", "rename",
                                        "source", "$.clientId",
                                        "target", "$.app_id")))),
                "endpoints", List.of(Map.of(
                        "id", "echo",
                        "method", "GET",
                        "path", "/echo",
                        "enabled", true,
                        "mappingOverride", Map.of(
                                "request", Map.of(
                                        "script", "ctx.bodyAsMap()"))))));

        EndpointSpec endpoint = spec.endpoints().getFirst();
        assertThat(spec.mapping().request().rules()).hasSize(1);
        assertThat(endpoint.mappingOverride()).isNotNull();
        assertThat(endpoint.mappingOverride().request()).isNotNull();
        assertThat(endpoint.mappingOverride().request().script()).isEqualTo("ctx.bodyAsMap()");
        assertThat(endpoint.mappingOverride().request().rules()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static ConnectorSpec parseFixture(String resourcePath) {
        InputStream in = ConnectorSpecParserMappingTest.class.getClassLoader().getResourceAsStream(resourcePath);
        assertThat(in).as("fixture %s", resourcePath).isNotNull();
        Map<String, Object> root = new Yaml().load(in);
        return ConnectorSpecParser.parse(root);
    }
}
