/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.support;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointParamSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog 端点结构断言（厂家 Spec 回归）。
 */
public final class CatalogEndpointAssertions {

    private CatalogEndpointAssertions() {
    }

    public static void assertCatalogContract(
            ConnectorSpec spec,
            String code3rd,
            String authType,
            String baseUrlPrefix,
            Set<String> expectedEndpointIds) {
        assertThat(spec.code3rd()).isEqualTo(code3rd);
        assertThat(spec.baseUrl()).startsWith(baseUrlPrefix);
        assertThat(spec.auth()).containsEntry("type", authType);
        assertThat(spec.response()).isNotNull();
        assertThat(spec.endpoints()).hasSize(expectedEndpointIds.size());
        assertThat(spec.endpoints().stream().map(EndpointSpec::id).toList())
                .containsExactlyInAnyOrderElementsOf(expectedEndpointIds);
        assertEndpointIntegrity(spec);
    }

    public static void assertEndpointIntegrity(ConnectorSpec spec) {
        List<String> ids = spec.endpoints().stream().map(EndpointSpec::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
        for (EndpointSpec endpoint : spec.endpoints()) {
            assertThat(endpoint.id()).isNotBlank();
            assertThat(endpoint.method()).isIn("GET", "POST", "PUT", "DELETE", "PATCH");
            assertThat(endpoint.path()).startsWith("/");
            assertThat(endpoint.enabled()).isTrue();
            assertThat(endpoint.doc()).isNotNull();
            assertThat(endpoint.doc().summary()).isNotBlank();
        }
    }

    public static EndpointSpec requireEndpoint(ConnectorSpec spec, String endpointId) {
        return spec.endpoints().stream()
                .filter(endpoint -> endpointId.equals(endpoint.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing endpoint: " + endpointId));
    }

    public static void assertRequiredQueryParams(EndpointSpec endpoint, String... requiredNames) {
        List<String> required = endpoint.doc().parameters().stream()
                .filter(param -> Boolean.TRUE.equals(param.required()))
                .map(EndpointParamSpec::name)
                .toList();
        assertThat(required).containsExactlyInAnyOrder(requiredNames);
    }

    public static void assertQueryParamNames(EndpointSpec endpoint, String... names) {
        assertThat(endpoint.doc().parameters().stream().map(EndpointParamSpec::name).toList())
                .containsAll(List.of(names));
    }
}
