/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.connectors.demo;

import com.suntek.integration.spec.catalog.CatalogAuth;
import com.suntek.integration.spec.catalog.CatalogConnector;
import com.suntek.integration.spec.catalog.CatalogResponse;
import com.suntek.integration.spec.catalog.HttpGet;

@CatalogConnector(code3rd = "DEMO_AKSK", baseUrl = "https://httpbin.org")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(dataPath = "$.url")
public interface DemoAkskConnectorCatalog {

    @HttpGet("/get")
    void echoGet();
}
