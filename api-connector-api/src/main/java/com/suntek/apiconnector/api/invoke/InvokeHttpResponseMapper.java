/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

import com.suntek.apiconnector.api.config.IntegrationInvokeProperties;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 将 {@link ProxyInvokeResponse} 映射为 HTTP 响应（平台状态码策略）。
 */
@Component
public class InvokeHttpResponseMapper {

    private final IntegrationInvokeProperties properties;

    public InvokeHttpResponseMapper(IntegrationInvokeProperties properties) {
        this.properties = properties;
    }

    public ResponseEntity<ProxyInvokeResponse> toResponse(ProxyInvokeResponse body) {
        int status = properties.resolveHttpStatus(body.getVendorHttpStatus(), body.isSuccess());
        body.setHttpStatus(status);
        return ResponseEntity.status(status).body(body);
    }

    public ResponseEntity<String> toLegacyRawBody(ProxyInvokeResponse body) {
        int status = resolveLegacyHttpStatus(body);
        String raw = body.getRawBody() != null ? body.getRawBody() : "";
        return ResponseEntity.status(status).body(raw);
    }

    public int resolveLegacyHttpStatus(ProxyInvokeResponse body) {
        return properties.resolveHttpStatus(body.getVendorHttpStatus(), body.isSuccess());
    }
}
