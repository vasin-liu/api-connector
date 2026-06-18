/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import com.suntek.apiconnector.api.dto.ProxyInvokeRequest;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import com.suntek.apiconnector.api.invoke.InvokeContext;
import com.suntek.apiconnector.api.invoke.InvokeHttpResponseMapper;
import com.suntek.apiconnector.api.service.IntegrationInvokeService;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final ConnectorRegistry registry;

    public ThirdpartLegacyDispatcher(
            IntegrationInvokeService invokeService,
            InvokeHttpResponseMapper responseMapper,
            LegacyCompatResponseFormatter responseFormatter,
            ConnectorRegistry registry) {
        this.invokeService = invokeService;
        this.responseMapper = responseMapper;
        this.responseFormatter = responseFormatter;
        this.registry = registry;
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
        // 03-03 (Option A / PIPE-02): when this already-adapted (method, path) unambiguously
        // matches exactly one configured endpoint, supply its endpointId so the shared
        // orchestrator runs request/response mapping (D-08). Zero or multiple matches leave
        // endpointId null -> the engine's D-04 passthrough invariant is preserved unchanged.
        resolveEndpointId(code3rd, method, path).ifPresent(request::setEndpointId);
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

    /**
     * Resolves the connector endpoint that uniquely matches an already-adapted (method, path).
     *
     * <p>03-03 / PIPE-02 (Option A): legacy routes historically dispatched with a null
     * {@code endpointId}, which the engine treats as a mapping passthrough (D-04). This made
     * legacy URLs that DO correspond to a configured, mapped endpoint silently skip mapping.
     * Here we look up the connector's enabled endpoints and, only when exactly one matches the
     * given (method, path), return its id so the caller can set it on the {@link ProxyInvokeRequest}.
     *
     * <p>Invariant preservation: zero matches (free-path/IDPS-style routes with no configured
     * endpoint) or multiple matches both yield {@link Optional#empty()}, leaving {@code endpointId}
     * null so the engine's passthrough behavior is unchanged — legacy-compat safe for every
     * existing route. Any lookup failure (unknown connector, etc.) is swallowed to null for the
     * same reason; the subsequent {@code invokeService.invoke} performs the authoritative lookup.
     *
     * @param code3rd connector code
     * @param method  HTTP method of the adapted legacy request
     * @param path    vendor path of the adapted legacy request (post alias/normalization, D-07)
     * @return the uniquely matching endpoint id, or empty when ambiguous/absent
     */
    private Optional<String> resolveEndpointId(String code3rd, String method, String path) {
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            ConnectorSpec spec = registry.require(code3rd);
            List<EndpointSpec> endpoints = spec.endpoints();
            if (endpoints == null || endpoints.isEmpty()) {
                return Optional.empty();
            }
            String wantedMethod = method.trim();
            String wantedPath = normalizePath(path);
            String matchedId = null;
            for (EndpointSpec endpoint : endpoints) {
                if (endpoint.enabled() != null && !endpoint.enabled()) {
                    continue;
                }
                String endpointMethod = endpoint.method() != null ? endpoint.method() : "GET";
                if (!endpointMethod.equalsIgnoreCase(wantedMethod)) {
                    continue;
                }
                if (!normalizePath(endpoint.path()).equals(wantedPath)) {
                    continue;
                }
                if (matchedId != null) {
                    // Ambiguous: more than one endpoint maps to this (method, path) -> passthrough.
                    return Optional.empty();
                }
                matchedId = endpoint.id();
            }
            return Optional.ofNullable(matchedId);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
