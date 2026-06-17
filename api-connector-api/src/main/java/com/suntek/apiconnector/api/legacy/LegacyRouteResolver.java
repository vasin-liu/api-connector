/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 按最长前缀匹配 legacy 路由。
 */
@Component
public class LegacyRouteResolver {

    private final IntegrationLegacyProperties properties;

    public LegacyRouteResolver(IntegrationLegacyProperties properties) {
        this.properties = properties;
    }

    public Optional<ResolvedRoute> resolve(String requestUri) {
        if (requestUri == null || requestUri.isBlank() || !properties.isEnabled()) {
            return Optional.empty();
        }
        List<IntegrationLegacyProperties.RouteMapping> routes = properties.getRoutes();
        return routes.stream()
                .filter(r -> r.getPathPrefix() != null && !r.getPathPrefix().isBlank())
                .filter(r -> matchesPrefix(requestUri, r.getPathPrefix()))
                .max(Comparator.comparingInt(r -> r.getPathPrefix().length()))
                .map(r -> new ResolvedRoute(r, remainder(requestUri, r.getPathPrefix())));
    }

    private static boolean matchesPrefix(String uri, String prefix) {
        if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
            return true;
        }
        return false;
    }

    private static String remainder(String uri, String prefix) {
        String rest = uri.substring(prefix.length());
        return rest.isBlank() ? "/" : rest;
    }

    public record ResolvedRoute(IntegrationLegacyProperties.RouteMapping mapping, String pathAfterPrefix) {
        public String code3rd() {
            return mapping.getCode3rd();
        }

        public LegacyResponseStyle responseStyle() {
            return mapping.getResponseStyle() != null
                    ? mapping.getResponseStyle()
                    : LegacyResponseStyle.VENDOR_RAW;
        }
    }
}
