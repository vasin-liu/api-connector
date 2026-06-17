/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.idps;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpGet;
import com.suntek.apiconnector.spec.catalog.HttpPost;
import com.suntek.apiconnector.spec.catalog.QueryNames;

/**
 * IDPS 城市交通大脑（对应 system-thirdpart IdpsClient）。
 */
@CatalogConnector(code3rd = "IDPS", baseUrl = "https://idps.example.com")
@CatalogAuth(type = "aksk_hmac_sha256_v1", accessKeyRef = "publicKey", secretKeyRef = "appSecret")
@CatalogResponse(successWhen = "$.success", dataPath = "$.obj", messagePath = "$.msg")
public interface IdpsConnectorCatalog {

    @HttpGet("/api/v2/traffic-aware/road-aware/domain-roads")
    @QueryNames(value = {"domain_code", "domain_type", "page", "page_size"}, required = {"domain_code", "domain_type"})
    void domainRoads();

    @HttpGet("/api/v2/traffic-aware/road-aware/speeds")
    @QueryNames(value = {"roadclid", "from_time", "to_time"}, required = {"roadclid"})
    void roadSpeeds();

    @HttpGet("/api/v2/traffic-aware/road-aware/flows")
    @QueryNames(value = {"roadclid", "from_time", "to_time"}, required = {"roadclid"})
    void roadFlows();

    @HttpGet("/api/v2/traffic-aware/road-aware/congestion-indexes")
    @QueryNames(value = {"roadclid", "from_time", "to_time"}, required = {"roadclid"})
    void roadCongestionIndexes();

    @HttpGet("/api/v2/traffic-aware/road-aware/congestion-miles")
    @QueryNames(value = {"roadclid", "from_time", "to_time"}, required = {"roadclid"})
    void roadCongestionMiles();

    @HttpGet("/api/v2/traffic-aware/macro-aware/road-speeds")
    @QueryNames(value = {"district", "from_time", "to_time"}, required = {"district"})
    void macroRoadSpeeds();

    @HttpGet("/api/v2/traffic-aware/macro-aware/congestion-miles")
    @QueryNames(value = {"domain_code", "domain_type", "from_time", "to_time"}, required = {"domain_code", "domain_type"})
    void macroCongestionMiles();

    @HttpGet("/api/v2/traffic-aware/macro-aware/congestion-indexes")
    @QueryNames(value = {"district", "from_time", "to_time"}, required = {"district"})
    void macroCongestionIndexes();

    @HttpGet("/api/v2/traffic-aware/macro-aware/on-road-car-nums")
    @QueryNames(value = {"district", "from_time", "to_time"}, required = {"district"})
    void onRoadCarNums();

    @HttpGet("/api/v2/biz-empower/traffic-situation/road-congestion-sort")
    @QueryNames(value = {"ana_date", "domain_code", "domain_type", "topn", "order_by"}, required = {"ana_date", "domain_code", "domain_type", "topn"})
    void roadCongestionSort();

    @HttpGet("/api/v2/traffic-aware/macro-aware/congestion-roads-ranking")
    @QueryNames(value = {"date_type", "date", "order_field", "order_type"}, required = {"date_type", "date"})
    void congestionRoadsRanking();

    @HttpGet("/api/v2/biz-empower/traffic-situation/subdomain-congestion-sort")
    @QueryNames(value = {"ana_date", "domain_code", "domain_type", "topn", "order_by"}, required = {"ana_date", "domain_code", "domain_type", "topn"})
    void subdomainCongestionSort();

    @HttpGet("/api/v2/biz-empower/traffic-situation/frequently-road-congestions-his")
    @QueryNames(value = {"domain_code", "domain_type", "from_time", "to_time", "page", "page_size"}, required = {"domain_code", "domain_type"})
    void frequentlyRoadCongestionsHis();

    @HttpGet("/api/v2/biz-empower/icv/travel-feature/high-fre-roads")
    @QueryNames(value = {"type"}, required = {"type"})
    void travelFeatureHighFreRoads();

    @HttpGet("/api/v2/biz-empower/icv/travel-feature/average_daily_travel_time")
    void travelFeatureAverageDailyTravelTime();

    @HttpGet("/api/v2/ds/park/push-available-spaces")
    void parkPushAvailableSpaces();

    @HttpGet("/api/v2/biz-empower/traffic-situation/congestion-reasons-suggestions")
    @QueryNames({"district", "date_type", "from_time", "to_time", "page", "page_size"})
    void congestionReasonsSuggestions();

    @HttpGet("/api/v2/biz-empower/traffic-situation/congestion-reasons-suggestions-details")
    @QueryNames(value = {"crossid", "date_type", "from_time", "to_time"}, required = {"crossid"})
    void congestionReasonsSuggestionsDetails();

    @HttpPost("/brain-auth/check/getUserInfo")
    @QueryNames(value = {"token"}, required = {"token"})
    void getUserInfo();
}
