/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors;

import com.suntek.apiconnector.connectors.support.CatalogEndpointAssertions;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产厂家 Catalog 端点清单与契约测试。
 */
class ProductionConnectorCatalogsTest {

    private static final Set<String> IDPS_ENDPOINTS = Set.of(
            "domainRoads",
            "roadSpeeds",
            "roadFlows",
            "roadCongestionIndexes",
            "roadCongestionMiles",
            "macroRoadSpeeds",
            "macroCongestionMiles",
            "macroCongestionIndexes",
            "onRoadCarNums",
            "roadCongestionSort",
            "congestionRoadsRanking",
            "subdomainCongestionSort",
            "frequentlyRoadCongestionsHis",
            "travelFeatureHighFreRoads",
            "travelFeatureAverageDailyTravelTime",
            "parkPushAvailableSpaces",
            "congestionReasonsSuggestions",
            "congestionReasonsSuggestionsDetails",
            "getUserInfo");

    private static final Set<String> GAODE_OPEN_PLATFORM_ENDPOINTS = Set.of(
            "trafficStatusRectangle", "placeAround", "geocodeGeo", "directionDriving");

    private static final Set<String> GAODE_TRAFFIC_ENDPOINTS = Set.of(
            "trafficEventByAdcode",
            "indexRoadRanking",
            "indexDistrictRanking",
            "indexHistoryDistrictRanking",
            "congestionRealtime",
            "congestionHistory",
            "stateDriving",
            "roadPredict",
            "listInter");

    private static final Set<String> BAIDU_MAP_ENDPOINTS = Set.of(
            "placeSuggestion", "directionDriving", "geocoding", "reverseGeocoding");

    private static final Set<String> BAIDU_WENXIN_ENDPOINTS = Set.of("chatCompletionsPro", "embeddingV1");

    @Test
    void idpsCatalogContract() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("IDPS");
        CatalogEndpointAssertions.assertCatalogContract(
                spec, "IDPS", "aksk_hmac_sha256_v1", "https://idps.", IDPS_ENDPOINTS);

        EndpointSpec roadSpeeds = CatalogEndpointAssertions.requireEndpoint(spec, "roadSpeeds");
        assertThat(roadSpeeds.method()).isEqualTo("GET");
        assertThat(roadSpeeds.path()).isEqualTo("/api/v2/traffic-aware/road-aware/speeds");
        CatalogEndpointAssertions.assertRequiredQueryParams(roadSpeeds, "roadclid");

        EndpointSpec getUserInfo = CatalogEndpointAssertions.requireEndpoint(spec, "getUserInfo");
        assertThat(getUserInfo.method()).isEqualTo("POST");
        assertThat(getUserInfo.path()).isEqualTo("/brain-auth/check/getUserInfo");
        CatalogEndpointAssertions.assertRequiredQueryParams(getUserInfo, "token");
    }

    @Test
    void gaodeOpenPlatformCatalogContract() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("GAODE_OPEN_PLATFORM");
        CatalogEndpointAssertions.assertCatalogContract(
                spec,
                "GAODE_OPEN_PLATFORM",
                "api_key_query",
                "https://restapi.amap.com",
                GAODE_OPEN_PLATFORM_ENDPOINTS);

        EndpointSpec rectangle = CatalogEndpointAssertions.requireEndpoint(spec, "trafficStatusRectangle");
        assertThat(rectangle.method()).isEqualTo("GET");
        assertThat(rectangle.path()).isEqualTo("/v3/traffic/status/rectangle");
        CatalogEndpointAssertions.assertQueryParamNames(rectangle, "rectangle", "level", "extensions");
    }

    @Test
    void gaodeTrafficCatalogContract() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("GAODE_TRAFFIC");
        CatalogEndpointAssertions.assertCatalogContract(
                spec,
                "GAODE_TRAFFIC",
                "gaode_traffic_hmac_v1",
                "https://et-api.amap.com",
                GAODE_TRAFFIC_ENDPOINTS);

        EndpointSpec realtime = CatalogEndpointAssertions.requireEndpoint(spec, "congestionRealtime");
        assertThat(realtime.method()).isEqualTo("POST");
        assertThat(realtime.path()).isEqualTo("/congestion/realtime");

        EndpointSpec listInter = CatalogEndpointAssertions.requireEndpoint(spec, "listInter");
        assertThat(listInter.method()).isEqualTo("GET");
        assertThat(listInter.path()).isEqualTo("/diagnosis/statics/listInter");
    }

    @Test
    void baiduMapCatalogContract() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("BAIDU_MAP");
        CatalogEndpointAssertions.assertCatalogContract(
                spec, "BAIDU_MAP", "api_key_query", "https://api.map.baidu.com", BAIDU_MAP_ENDPOINTS);

        EndpointSpec geocoding = CatalogEndpointAssertions.requireEndpoint(spec, "geocoding");
        assertThat(geocoding.method()).isEqualTo("GET");
        assertThat(geocoding.path()).isEqualTo("/geocoding/v3/");
    }

    @Test
    void baiduWenxinCatalogContract() {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec("BAIDU_WENXIN");
        CatalogEndpointAssertions.assertCatalogContract(
                spec,
                "BAIDU_WENXIN",
                "oauth2_token_in_query",
                "https://aip.baidubce.com",
                BAIDU_WENXIN_ENDPOINTS);

        EndpointSpec chat = CatalogEndpointAssertions.requireEndpoint(spec, "chatCompletionsPro");
        assertThat(chat.method()).isEqualTo("POST");
        assertThat(chat.path()).isEqualTo("/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro");
        assertThat(spec.auth()).containsEntry("tokenParam", "access_token");
    }

    @Test
    void baiduGptAliasMatchesWenxinEndpoints() {
        ConnectorSpec alias = BuiltinConnectorCatalogs.catalogSpec("BaiduGpt");
        ConnectorSpec canonical = BuiltinConnectorCatalogs.catalogSpec("BAIDU_WENXIN");
        assertThat(alias.code3rd()).isEqualTo("BaiduGpt");
        assertThat(alias.endpoints().stream().map(EndpointSpec::id).toList())
                .containsExactlyInAnyOrderElementsOf(
                        canonical.endpoints().stream().map(EndpointSpec::id).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"IDPS", "GAODE_OPEN_PLATFORM", "GAODE_TRAFFIC", "BAIDU_MAP", "BAIDU_WENXIN"})
    void productionCatalogsAreManaged(String code3rd) {
        assertThat(BuiltinConnectorCatalogs.isManaged(code3rd)).isTrue();
        assertThat(BuiltinConnectorCatalogs.catalogSpec(code3rd).endpoints()).isNotEmpty();
    }

    @Test
    void managedProductionCatalogCount() {
        assertThat(BuiltinConnectorCatalogs.managedCode3rds())
                .contains("IDPS", "GAODE_OPEN_PLATFORM", "GAODE_TRAFFIC", "BAIDU_MAP", "BAIDU_WENXIN", "BaiduGpt");
    }

    /**
     * Wave 1 auth profile IDs per docs/legacy-auth-inventory.md (D-03, ROADMAP SC#6).
     */
    @Test
    void wave1CatalogAuthTypesMatchLegacyInventory() {
        assertThat(BuiltinConnectorCatalogs.catalogSpec("IDPS").auth()).containsEntry("type", "aksk_hmac_sha256_v1");
        assertThat(BuiltinConnectorCatalogs.catalogSpec("GAODE_OPEN_PLATFORM").auth())
                .containsEntry("type", "api_key_query");
        assertThat(BuiltinConnectorCatalogs.catalogSpec("GAODE_TRAFFIC").auth())
                .containsEntry("type", "gaode_traffic_hmac_v1");
        assertThat(BuiltinConnectorCatalogs.catalogSpec("BAIDU_MAP").auth()).containsEntry("type", "api_key_query");
        assertThat(BuiltinConnectorCatalogs.catalogSpec("BAIDU_WENXIN").auth())
                .containsEntry("type", "oauth2_token_in_query");
    }
}
