/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec;

import com.suntek.apiconnector.spec.catalog.EndpointDocumentation;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorSpecParserTest {

    @Test
    void parseFromFlatMap() {
        ConnectorSpec spec = ConnectorSpecParser.parse(Map.of(
                "code3rd", "T1",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "endpoints", List.of(Map.of(
                        "id", "health",
                        "method", "GET",
                        "path", "/health",
                        "enabled", true))));

        assertThat(spec.code3rd()).isEqualTo("T1");
        assertThat(spec.auth()).containsEntry("type", "none");
        assertThat(spec.endpoints()).hasSize(1);
        assertThat(spec.endpoints().get(0).id()).isEqualTo("health");
        assertThat(spec.endpoints().get(0).doc()).isNotNull();
        assertThat(spec.endpoints().get(0).doc().summary()).isEqualTo("Health");
    }

    @Test
    void ignoresLegacyYamlDocBlockAndEnrichesAutomatically() {
        ConnectorSpec spec = ConnectorSpecParser.parse(Map.of(
                "code3rd", "T1",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "endpoints", List.of(Map.of(
                        "id", "roadSpeeds",
                        "method", "GET",
                        "path", "/api/v2/traffic-aware/road-aware/speeds",
                        "enabled", true,
                        "doc", Map.of(
                                "summary", "手动 YAML 文档",
                                "parameters", List.of(Map.of("name", "roadclid")))))));

        assertThat(spec.endpoints().get(0).doc().summary()).isEqualTo("Road Speeds");
        assertThat(EndpointDocumentation.inferGroupFromPath(spec.endpoints().get(0).path()))
                .isEqualTo("路况感知 · 道路");
    }

    @Test
    void parsesGroovyAuthOverrideOnEndpoint() {
        ConnectorSpec spec = ConnectorSpecParser.parse(Map.of(
                "code3rd", "GROOVY_DEMO",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "endpoints", List.of(Map.of(
                        "id", "special",
                        "method", "GET",
                        "path", "/special",
                        "authOverride", Map.of(
                                "type", "groovy_auth_script",
                                "script", "return null")))));

        assertThat(spec.endpoints().get(0).authOverride())
                .containsEntry("type", "groovy_auth_script");
    }

    @Test
    void rejectsNonGroovyAuthOverrideAtParse() {
        assertThatThrownBy(() -> ConnectorSpecParser.parse(Map.of(
                "code3rd", "BAD",
                "baseUrl", "https://example.com",
                "auth", Map.of("type", "none"),
                "endpoints", List.of(Map.of(
                        "id", "bad",
                        "method", "GET",
                        "path", "/bad",
                        "authOverride", Map.of("type", "bearer_static"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Groovy-only");
    }
}
