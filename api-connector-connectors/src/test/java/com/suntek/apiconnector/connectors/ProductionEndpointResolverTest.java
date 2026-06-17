/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors;

import com.suntek.apiconnector.connectors.support.CatalogEndpointAssertions;
import com.suntek.apiconnector.engine.EndpointResolver;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Catalog 登记的每个 endpointId 均可被引擎解析。
 */
class ProductionEndpointResolverTest {

    @ParameterizedTest
    @MethodSource("productionEndpointCases")
    void resolvesCatalogEndpoint(String code3rd, String endpointId, String method, String path) {
        ConnectorSpec spec = BuiltinConnectorCatalogs.catalogSpec(code3rd);
        var endpoint = CatalogEndpointAssertions.requireEndpoint(spec, endpointId);

        var resolved = EndpointResolver.resolve(spec, endpointId, null, null);

        assertThat(resolved.endpointId()).isEqualTo(endpointId);
        assertThat(resolved.method()).isEqualTo(endpoint.method()).isEqualTo(method);
        assertThat(resolved.path()).isEqualTo(endpoint.path()).isEqualTo(path);
    }

    private static Stream<Arguments> productionEndpointCases() {
        return Stream.of(
                Arguments.of("IDPS", "roadSpeeds", "GET", "/api/v2/traffic-aware/road-aware/speeds"),
                Arguments.of("IDPS", "getUserInfo", "POST", "/brain-auth/check/getUserInfo"),
                Arguments.of("GAODE_OPEN_PLATFORM", "trafficStatusRectangle", "GET", "/v3/traffic/status/rectangle"),
                Arguments.of("GAODE_TRAFFIC", "congestionRealtime", "POST", "/congestion/realtime"),
                Arguments.of("GAODE_TRAFFIC", "listInter", "GET", "/diagnosis/statics/listInter"),
                Arguments.of("BAIDU_MAP", "reverseGeocoding", "GET", "/reverse_geocoding/v3/"),
                Arguments.of("BAIDU_WENXIN", "embeddingV1", "POST", "/rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/embedding-v1"),
                Arguments.of("BaiduGpt", "chatCompletionsPro", "POST", "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro"));
    }
}
