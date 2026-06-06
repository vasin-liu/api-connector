package com.suntek.integration.api.legacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRouteResolverTest {

    @Test
    void picksLongestPrefix() {
        IntegrationLegacyProperties props = new IntegrationLegacyProperties();
        IntegrationLegacyProperties.RouteMapping gaodeTraffic = new IntegrationLegacyProperties.RouteMapping();
        gaodeTraffic.setPathPrefix("/gaode/traffic");
        gaodeTraffic.setCode3rd("GAODE_TRAFFIC");
        IntegrationLegacyProperties.RouteMapping gaode = new IntegrationLegacyProperties.RouteMapping();
        gaode.setPathPrefix("/gaode");
        gaode.setCode3rd("GAODE_OPEN_PLATFORM");
        props.setRoutes(java.util.List.of(gaodeTraffic, gaode));
        LegacyRouteResolver resolver = new LegacyRouteResolver(props);

        var traffic = resolver.resolve("/gaode/traffic/getRectangleTrafficInfo");
        assertThat(traffic).isPresent();
        assertThat(traffic.get().code3rd()).isEqualTo("GAODE_TRAFFIC");
        assertThat(traffic.get().pathAfterPrefix()).isEqualTo("/getRectangleTrafficInfo");

        var open = resolver.resolve("/gaode/placeAroundSearch");
        assertThat(open).isPresent();
        assertThat(open.get().code3rd()).isEqualTo("GAODE_OPEN_PLATFORM");
    }
}
