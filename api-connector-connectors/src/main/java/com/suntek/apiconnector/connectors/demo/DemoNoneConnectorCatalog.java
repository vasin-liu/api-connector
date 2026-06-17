/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.demo;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;

/**
 * 演示：无认证 HTTP 代理。
 */
@CatalogConnector(code3rd = "DEMO_NONE", baseUrl = "https://httpbin.org")
@CatalogAuth(type = "none")
@CatalogResponse(dataPath = "$.url")
public interface DemoNoneConnectorCatalog {

    @HttpGet("/get")
    void echoGet();
}
