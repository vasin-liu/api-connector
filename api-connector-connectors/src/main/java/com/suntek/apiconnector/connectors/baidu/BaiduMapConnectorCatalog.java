/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.baidu;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;

@CatalogConnector(code3rd = "BAIDU_MAP", baseUrl = "https://api.map.baidu.com")
@CatalogAuth(type = "api_key_query", keyRef = "appId", paramName = "ak")
@CatalogResponse(successWhen = "$.status == 0", dataPath = "$.result", messagePath = "$.message")
public interface BaiduMapConnectorCatalog {

    @HttpGet("/place/v2/suggestion")
    void placeSuggestion();

    @HttpGet("/directionlite/v1/driving")
    void directionDriving();

    @HttpGet("/geocoding/v3/")
    void geocoding();

    @HttpGet("/reverse_geocoding/v3/")
    void reverseGeocoding();
}
