/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.openapi;

import com.suntek.apiconnector.api.dto.EndpointParamSummary;
import com.suntek.apiconnector.spec.catalog.EndpointDocumentation;
import com.suntek.apiconnector.spec.model.EndpointDocSpec;
import com.suntek.apiconnector.spec.model.EndpointParamSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointOpenApiMetadataResolverTest {

    @Test
    void infersGroupFromVendorPath() {
        EndpointSpec endpoint = new EndpointSpec(
                "roadSpeeds", "GET", "/api/v2/traffic-aware/road-aware/speeds", null, true);
        assertThat(EndpointOpenApiMetadataResolver.resolveGroup(endpoint))
                .isEqualTo("路况感知 · 道路");
    }

    @Test
    void humanizesEndpointIdWhenSummaryMissing() {
        EndpointSpec endpoint = EndpointDocumentation.enrich(new EndpointSpec(
                "roadCongestionSort", "GET", "/x", null, true));
        assertThat(EndpointOpenApiMetadataResolver.resolveSummary(endpoint))
                .isEqualTo("Road Congestion Sort");
    }

    @Test
    void resolveParametersFromDoc() {
        EndpointSpec endpoint = new EndpointSpec(
                "roadSpeeds",
                "GET",
                "/api/v2/traffic-aware/road-aware/speeds",
                null,
                true,
                new EndpointDocSpec(
                        "Road Speeds",
                        null,
                        "路况感知 · 道路",
                        List.of(
                                new EndpointParamSpec("roadclid", "路段 ID", true, "", "query"),
                                new EndpointParamSpec("from_time", null, false, "", "query"))));
        assertThat(EndpointOpenApiMetadataResolver.resolveParameters(endpoint))
                .extracting(EndpointParamSummary::getName)
                .containsExactly("roadclid", "from_time");
    }
}
