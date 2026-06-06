package com.suntek.integration.api.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyForwardResolverTest {

    private final LegacyForwardResolver resolver = new LegacyForwardResolver(new ObjectMapper());

    @Test
    void gaodeTrafficRectangleAliasUsesOpenPlatform() {
        IntegrationLegacyProperties props = new IntegrationLegacyProperties();
        LegacyRouteResolver routeResolver = new LegacyRouteResolver(props);
        var route = routeResolver.resolve("/gaode/traffic/getRectangleTrafficInfo").orElseThrow();

        LegacyForwardPlan plan = resolver.resolve(
                route,
                HttpMethod.GET,
                java.util.Map.of("rectangle", "1,2;3,4", "level", "6"),
                null);

        assertThat(plan.code3rd()).isEqualTo("GAODE_OPEN_PLATFORM");
        assertThat(plan.path()).isEqualTo("/v3/traffic/status/rectangle");
        assertThat(plan.method()).isEqualTo("GET");
    }

    @Test
    void gaodeTrafficPostRewritesToGetWithReqBodyQuery() {
        IntegrationLegacyProperties props = new IntegrationLegacyProperties();
        LegacyRouteResolver routeResolver = new LegacyRouteResolver(props);
        var route = routeResolver.resolve("/gaode/traffic/event/queryByAdcode").orElseThrow();

        LegacyForwardPlan plan = resolver.resolve(
                route,
                HttpMethod.POST,
                java.util.Map.of(),
                "{\"data\":{\"adcode\":\"440100\"}}");

        assertThat(plan.method()).isEqualTo("GET");
        assertThat(plan.path()).isEqualTo("/event/queryByAdcode");
        assertThat(plan.query()).containsEntry("adcode", "440100");
    }

    @Test
    void gaodePlaceAroundSearchPostAlias() {
        IntegrationLegacyProperties props = new IntegrationLegacyProperties();
        LegacyRouteResolver routeResolver = new LegacyRouteResolver(props);
        var route = routeResolver.resolve("/gaode/placeAroundSearch").orElseThrow();

        LegacyForwardPlan plan = resolver.resolve(
                route,
                HttpMethod.POST,
                java.util.Map.of(),
                "{\"data\":{\"keywords\":\"咖啡\",\"location\":\"113,23\"}}");

        assertThat(plan.code3rd()).isEqualTo("GAODE_OPEN_PLATFORM");
        assertThat(plan.path()).isEqualTo("/v5/place/around");
        assertThat(plan.query()).containsEntry("keywords", "咖啡");
    }
}
