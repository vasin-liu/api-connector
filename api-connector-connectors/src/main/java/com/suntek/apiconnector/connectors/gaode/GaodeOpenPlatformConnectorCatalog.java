/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.gaode;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;
import com.suntek.apiconnector.spec.catalog.QueryNames;

@CatalogConnector(code3rd = "GAODE_OPEN_PLATFORM", baseUrl = "https://restapi.amap.com")
@CatalogAuth(type = "api_key_query", keyRef = "publicKey", paramName = "key")
@CatalogResponse(successWhen = "$.status == 1", dataPath = "$")
public interface GaodeOpenPlatformConnectorCatalog {

    @HttpGet("/v3/traffic/status/rectangle")
    @QueryNames({"rectangle", "level", "extensions"})
    void trafficStatusRectangle();

    @HttpGet("/v5/place/around")
    void placeAround();

    @HttpGet("/v3/geocode/geo")
    void geocodeGeo();

    @HttpGet("/v3/direction/driving")
    void directionDriving();
}
