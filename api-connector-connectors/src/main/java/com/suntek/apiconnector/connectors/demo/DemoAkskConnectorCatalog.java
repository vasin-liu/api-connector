/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.demo;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;

@CatalogConnector(code3rd = "DEMO_AKSK", baseUrl = "https://httpbin.org")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(dataPath = "$.url")
public interface DemoAkskConnectorCatalog {

    @HttpGet("/get")
    void echoGet();
}
