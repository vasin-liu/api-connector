/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

import java.util.Objects;

/**
 * 将 {@code endpointId} 或显式 path/method 解析为出站 HTTP 调用参数。
 */
public final class EndpointResolver {

    private EndpointResolver() {
    }

    /**
     * 解析结果。
     *
     * @param endpointId 命中的 Spec 端点 id，自由 path 时为 null
     * @param method     HTTP 方法
     * @param path       厂家 path
     */
    public record ResolvedInvocation(String endpointId, String method, String path) {
    }

    /**
     * 优先使用 {@code endpointId}；否则要求 {@code method} + {@code path}。
     *
     * @param spec        连接器规格
     * @param endpointId  Spec 端点 id，可空
     * @param method      HTTP 方法，可空（endpointId 命中时从 Spec 取）
     * @param path        厂家 path，可空（endpointId 命中时从 Spec 取）
     * @return 解析结果
     */
    public static ResolvedInvocation resolve(
            ConnectorSpec spec,
            String endpointId,
            String method,
            String path) {
        if (endpointId != null && !endpointId.isBlank()) {
            EndpointSpec endpoint = requireEndpoint(spec, endpointId.trim());
            String resolvedMethod = endpoint.method() != null ? endpoint.method().toUpperCase() : "GET";
            String resolvedPath = normalizePath(endpoint.path());
            if (method != null && !method.isBlank()
                    && !resolvedMethod.equalsIgnoreCase(method.trim())) {
                throw new IllegalArgumentException(
                        "method conflicts with endpoint " + endpointId + ": spec=" + resolvedMethod
                                + ", request=" + method);
            }
            if (path != null && !path.isBlank()
                    && !resolvedPath.equals(normalizePath(path))) {
                throw new IllegalArgumentException(
                        "path conflicts with endpoint " + endpointId + ": spec=" + resolvedPath
                                + ", request=" + path);
            }
            return new ResolvedInvocation(endpoint.id(), resolvedMethod, resolvedPath);
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method is required when endpointId is omitted");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required when endpointId is omitted");
        }
        return new ResolvedInvocation(null, method.trim().toUpperCase(), normalizePath(path));
    }

    private static EndpointSpec requireEndpoint(ConnectorSpec spec, String endpointId) {
        if (spec.endpoints() == null || spec.endpoints().isEmpty()) {
            throw new IllegalArgumentException("Connector has no endpoints: " + spec.code3rd());
        }
        for (EndpointSpec endpoint : spec.endpoints()) {
            if (endpoint.enabled() != null && !endpoint.enabled()) {
                continue;
            }
            if (Objects.equals(endpointId, endpoint.id())) {
                return endpoint;
            }
        }
        throw new IllegalArgumentException(
                "Unknown or disabled endpoint '" + endpointId + "' for connector " + spec.code3rd());
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
