/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors;

import com.suntek.apiconnector.connectors.idps.IdpsConnectorCatalog;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinConnectorCatalogsTest {

    @Test
    void scanIdpsDirectly() {
        ConnectorSpec spec = com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(IdpsConnectorCatalog.class);
        assertThat(spec.code3rd()).isEqualTo("IDPS");
        assertThat(spec.auth()).containsEntry("type", "aksk_hmac_sha256_v1");
        assertThat(spec.endpoints()).hasSize(19);
    }
}
