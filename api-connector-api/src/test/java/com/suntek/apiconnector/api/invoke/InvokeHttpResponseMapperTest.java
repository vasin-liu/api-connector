package com.suntek.apiconnector.api.invoke;

import com.suntek.apiconnector.api.config.IntegrationInvokeProperties;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvokeHttpResponseMapperTest {

    @Test
    void platformOkModeAlwaysReturns200() {
        IntegrationInvokeProperties props = new IntegrationInvokeProperties();
        props.setPlatformHttpStatus(IntegrationInvokeProperties.PlatformHttpStatusMode.PLATFORM_OK);
        InvokeHttpResponseMapper mapper = new InvokeHttpResponseMapper(props);
        ProxyInvokeResponse body = ProxyInvokeResponse.builder()
                .success(false)
                .vendorHttpStatus(502)
                .httpStatus(502)
                .build();
        assertEquals(200, mapper.toResponse(body).getStatusCode().value());
    }

    @Test
    void vendorModePassesThroughStatus() {
        IntegrationInvokeProperties props = new IntegrationInvokeProperties();
        props.setPlatformHttpStatus(IntegrationInvokeProperties.PlatformHttpStatusMode.VENDOR);
        InvokeHttpResponseMapper mapper = new InvokeHttpResponseMapper(props);
        ProxyInvokeResponse body = ProxyInvokeResponse.builder()
                .vendorHttpStatus(502)
                .httpStatus(502)
                .build();
        assertEquals(502, mapper.toResponse(body).getStatusCode().value());
    }
}
