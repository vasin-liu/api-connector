/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.gaode;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;
import com.suntek.apiconnector.spec.catalog.HttpPost;

@CatalogConnector(code3rd = "GAODE_TRAFFIC", baseUrl = "https://et-api.amap.com")
@CatalogAuth(type = "gaode_traffic_hmac_v1", clientKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(successWhen = "$.code == 0", dataPath = "$.data", messagePath = "$.msg")
public interface GaodeTrafficConnectorCatalog {

    @HttpGet("/event/queryByAdcode")
    void trafficEventByAdcode();

    @HttpGet("/index/roadRanking")
    void indexRoadRanking();

    @HttpGet("/index/districtRanking")
    void indexDistrictRanking();

    @HttpGet("/index/historyDistrictRanking")
    void indexHistoryDistrictRanking();

    @HttpPost("/congestion/realtime")
    void congestionRealtime();

    @HttpPost("/congestion/history")
    void congestionHistory();

    @HttpGet("/state/driving")
    void stateDriving();

    @HttpGet("/predict/roadPredict")
    void roadPredict();

    @HttpGet("/diagnosis/statics/listInter")
    void listInter();
}
