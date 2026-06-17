package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuthConfigResolverTest {

    @Test
    void endpointAuthOverrideReplacesConnectorAuth() {
        Map<String, Object> connectorAuth = Map.of("type", "none");
        Map<String, Object> override = Map.of("type", "groovy_auth_script", "script", "return AuthOutcome.empty()");
        ConnectorSpec spec = new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                connectorAuth,
                List.of(new EndpointSpec("ep1", "GET", "/ep1", null, true, null, override)),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null,
                null);
        EndpointSpec endpoint = spec.endpoints().getFirst();

        Map<String, Object> resolved = AuthConfigResolver.resolve(spec, endpoint);

        assertSame(override, resolved);
        assertEquals("groovy_auth_script", resolved.get("type"));
    }

    @Test
    void nullEndpointUsesConnectorAuth() {
        Map<String, Object> connectorAuth = Map.of("type", "bearer_static");
        ConnectorSpec spec = new ConnectorSpec(
                "TEST",
                "1.0.0",
                "https://vendor.example.com",
                "HTTP",
                connectorAuth,
                List.of(),
                new ResponseSpec("true", "$", "$", "$"),
                null,
                null,
                null);

        Map<String, Object> resolved = AuthConfigResolver.resolve(spec, null);

        assertSame(connectorAuth, resolved);
        assertEquals("bearer_static", resolved.get("type"));
    }
}
