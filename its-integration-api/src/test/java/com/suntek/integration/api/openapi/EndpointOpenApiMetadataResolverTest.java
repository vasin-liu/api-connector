/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.openapi;

import com.suntek.integration.spec.catalog.EndpointDocumentation;
import com.suntek.integration.spec.model.EndpointSpec;
import org.junit.jupiter.api.Test;

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
}
