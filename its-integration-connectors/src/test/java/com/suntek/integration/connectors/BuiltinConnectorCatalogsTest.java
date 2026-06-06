/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.connectors;

import com.suntek.integration.connectors.idps.IdpsConnectorCatalog;
import com.suntek.integration.spec.model.ConnectorSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinConnectorCatalogsTest {

    @Test
    void baiduWenxinAliasResolvesToSameCatalog() {
        ConnectorSpec canonical = BuiltinConnectorCatalogs.catalogSpec("BAIDU_WENXIN");
        ConnectorSpec alias = BuiltinConnectorCatalogs.catalogSpec("BaiduGpt");
        assertThat(alias.code3rd()).isEqualTo("BaiduGpt");
        assertThat(alias.endpoints()).hasSize(canonical.endpoints().size());
        assertThat(BuiltinConnectorCatalogs.isManaged("BaiduGpt")).isTrue();
    }

    @Test
    void idpsCatalogHasEndpointsWithDerivedDoc() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("IDPS");
        assertThat(spec.endpoints()).hasSize(19);
        var roadSpeeds = spec.endpoints().stream()
                .filter(e -> "roadSpeeds".equals(e.id()))
                .findFirst()
                .orElseThrow();
        assertThat(roadSpeeds.doc().summary()).isEqualTo("Road Speeds");
        assertThat(roadSpeeds.doc().group()).isEqualTo("路况感知 · 道路");
        assertThat(roadSpeeds.doc().parameters()).extracting(p -> p.name())
                .contains("roadclid", "from_time", "to_time");
    }

    @Test
    void scanIdpsDirectly() {
        ConnectorSpec spec = com.suntek.integration.spec.catalog.CatalogConnectorScanner.scan(IdpsConnectorCatalog.class);
        assertThat(spec.code3rd()).isEqualTo("IDPS");
        assertThat(spec.auth()).containsEntry("type", "aksk_hmac_sha256_v1");
    }
}
