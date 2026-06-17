/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogManagedSpecMergerTest {

    @Test
    void runtimeMergeKeepsCatalogEndpointsAndAllowsBaseUrlOverride() {
        ConnectorSpec catalog = BuiltinConnectorCatalogs.catalogSpec("IDPS");
        ConnectorSpec stored = new ConnectorSpec(
                "IDPS",
                "9.9.9",
                "https://idps.prod.example.com",
                "HTTP",
                java.util.Map.of("type", "none"),
                java.util.List.of(),
                null,
                java.util.Map.of(),
                null,
                java.util.List.of());

        ConnectorSpec merged = CatalogManagedSpecMerger.forRuntime(stored);

        assertThat(merged.baseUrl()).isEqualTo("https://idps.prod.example.com");
        assertThat(merged.endpoints()).hasSize(catalog.endpoints().size());
        assertThat(merged.endpoints().stream().map(e -> e.id()).toList())
                .contains("domainRoads", "roadSpeeds");
        assertThat(merged.auth()).containsEntry("type", "aksk_hmac_sha256_v1");
    }

    @Test
    void cannotDeleteCatalogManagedConnector() {
        assertThatThrownBy(() -> CatalogManagedSpecMerger.assertDeletable("IDPS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog-managed");
    }
}
