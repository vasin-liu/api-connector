package com.suntek.integration.engine;

import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointResolverTest {

    private static ConnectorSpec specWithEndpoint() {
        return new ConnectorSpec(
                "IDPS",
                "1.0.0",
                "https://idps.example.com",
                "HTTP",
                null,
                java.util.List.of(new EndpointSpec(
                        "roadSpeeds", "GET", "/api/v2/traffic-aware/road-aware/speeds", null, true)),
                null,
                null,
                null);
    }

    @Test
    void resolvesByEndpointId() {
        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                specWithEndpoint(), "roadSpeeds", null, null);
        assertEquals("roadSpeeds", resolved.endpointId());
        assertEquals("GET", resolved.method());
        assertEquals("/api/v2/traffic-aware/road-aware/speeds", resolved.path());
    }

    @Test
    void resolvesFreePath() {
        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                specWithEndpoint(), null, "post", "/custom");
        assertNull(resolved.endpointId());
        assertEquals("POST", resolved.method());
        assertEquals("/custom", resolved.path());
    }

    @Test
    void rejectsUnknownEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> EndpointResolver.resolve(
                specWithEndpoint(), "missing", null, null));
    }
}
