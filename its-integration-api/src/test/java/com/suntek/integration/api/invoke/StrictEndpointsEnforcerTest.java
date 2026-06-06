package com.suntek.integration.api.invoke;

import com.suntek.integration.api.dto.ProxyInvokeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictEndpointsEnforcerTest {

    @Test
    void blocksFreePathOnManagedIdps() {
        ProxyInvokeRequest request = new ProxyInvokeRequest();
        request.setMethod("GET");
        request.setPath("/any");
        assertThrows(IllegalArgumentException.class, () ->
                StrictEndpointsEnforcer.requireEndpointIdForManaged("IDPS", request));
    }

    @Test
    void allowsEndpointIdOnManagedIdps() {
        ProxyInvokeRequest request = new ProxyInvokeRequest();
        request.setEndpointId("domainRoads");
        assertDoesNotThrow(() -> StrictEndpointsEnforcer.requireEndpointIdForManaged("IDPS", request));
    }
}
