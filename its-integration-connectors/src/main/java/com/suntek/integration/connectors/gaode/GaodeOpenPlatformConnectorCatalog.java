/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.connectors.gaode;

import com.suntek.integration.spec.catalog.CatalogAuth;
import com.suntek.integration.spec.catalog.CatalogConnector;
import com.suntek.integration.spec.catalog.CatalogResponse;
import com.suntek.integration.spec.catalog.HttpGet;
import com.suntek.integration.spec.catalog.QueryNames;

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
