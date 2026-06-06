/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

import com.suntek.integration.api.dto.ProxyInvokeRequest;
import com.suntek.integration.api.dto.ProxyInvokeResponse;
import com.suntek.integration.api.invoke.InvokeContext;
import com.suntek.integration.api.invoke.InvokeHttpResponseMapper;
import com.suntek.integration.api.service.IntegrationInvokeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 将旧 thirdpart 风格请求转发到统一 Invoke 编排。
 */
@Component
public class ThirdpartLegacyDispatcher {

    private final IntegrationInvokeService invokeService;
    private final InvokeHttpResponseMapper responseMapper;
    private final LegacyCompatResponseFormatter responseFormatter;

    public ThirdpartLegacyDispatcher(
            IntegrationInvokeService invokeService,
            InvokeHttpResponseMapper responseMapper,
            LegacyCompatResponseFormatter responseFormatter) {
        this.invokeService = invokeService;
        this.responseMapper = responseMapper;
        this.responseFormatter = responseFormatter;
    }

    public ResponseEntity<String> forwardGet(
            String code3rd, String path, Map<String, String> query, LegacyResponseStyle style) {
        return forward(new LegacyForwardPlan(code3rd, "GET", normalizePath(path), query, null, style));
    }

    public ResponseEntity<String> forwardPost(
            String code3rd, String path, Map<String, String> query, String body, LegacyResponseStyle style) {
        return forward(new LegacyForwardPlan(code3rd, "POST", normalizePath(path), query, body, style));
    }

    public ResponseEntity<String> forward(LegacyForwardPlan plan) {
        LegacyResponseStyle style = plan.responseStyle() != null
                ? plan.responseStyle()
                : LegacyResponseStyle.VENDOR_RAW;
        return forward(plan.code3rd(), plan.method(), plan.path(), plan.query(), plan.body(), style);
    }

    public ResponseEntity<String> forwardExchange(String code3rd, LegacyGetExchangeRequest exchange) {
        if (exchange.getUri() == null || exchange.getUri().isBlank()) {
            throw new IllegalArgumentException("uri is required");
        }
        return forwardGet(code3rd, exchange.getUri(), exchange.getQueryParameters(), LegacyResponseStyle.VENDOR_RAW);
    }

    private ResponseEntity<String> forward(
            String code3rd,
            String method,
            String path,
            Map<String, String> query,
            String body,
            LegacyResponseStyle style) {
        ProxyInvokeRequest request = new ProxyInvokeRequest();
        request.setMethod(method);
        request.setPath(path);
        request.setQuery(query != null ? query : Map.of());
        request.setBody(body);
        ProxyInvokeResponse invokeBody = invokeService.invoke(code3rd, request, InvokeContext.LEGACY);
        String payload = responseFormatter.format(invokeBody, style);
        int status = responseMapper.resolveLegacyHttpStatus(invokeBody);
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(payload);
    }

    public static Map<String, String> queryParams(HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                map.put(key, values[0]);
            }
        });
        return map;
    }

    public static String servletPathAfterPrefix(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        if (uri.startsWith(prefix)) {
            String remainder = uri.substring(prefix.length());
            return remainder.isBlank() ? "/" : remainder;
        }
        return uri;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
