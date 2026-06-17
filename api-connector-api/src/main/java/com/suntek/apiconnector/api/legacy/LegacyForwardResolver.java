/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 legacy 请求解析为 {@link LegacyForwardPlan}（含路径别名与 POST→GET 改写）。
 */
@Component
public class LegacyForwardResolver {

    private static final List<LegacyPathAlias> BUILTIN_ALIASES = List.of(
            alias("/gaode", "/placeAroundSearch", "GAODE_OPEN_PLATFORM", "GET", "/v5/place/around", true),
            alias(
                    "/gaode/traffic",
                    "/getRectangleTrafficInfo",
                    "GAODE_OPEN_PLATFORM",
                    "GET",
                    "/v3/traffic/status/rectangle",
                    false));

    private final ObjectMapper objectMapper;

    public LegacyForwardResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LegacyForwardPlan resolve(
            LegacyRouteResolver.ResolvedRoute route,
            HttpMethod httpMethod,
            Map<String, String> servletQuery,
            String body) {
        Optional<LegacyPathAlias> alias = findAlias(route, httpMethod);
        if (alias.isPresent()) {
            LegacyPathAlias a = alias.get();
            Map<String, String> query = servletQuery != null ? servletQuery : Map.of();
            if (a.reqBodyToQuery()) {
                query = LegacyReqBodySupport.mergeQuery(query, LegacyReqBodySupport.queryFromBody(objectMapper, body));
            }
            LegacyResponseStyle style = a.code3rd().equals(route.code3rd())
                    ? route.responseStyle()
                    : LegacyResponseStyle.SUNTEK_RESULT;
            return new LegacyForwardPlan(a.code3rd(), a.method(), a.vendorPath(), query, null, style);
        }
        if ("/gaode/traffic".equals(route.mapping().getPathPrefix())
                && HttpMethod.POST.equals(httpMethod)) {
            Map<String, String> query =
                    LegacyReqBodySupport.mergeQuery(servletQuery, LegacyReqBodySupport.queryFromBody(objectMapper, body));
            return new LegacyForwardPlan(route.code3rd(), "GET", route.pathAfterPrefix(), query, null, route.responseStyle());
        }
        return new LegacyForwardPlan(
                route.code3rd(),
                httpMethod.name(),
                route.pathAfterPrefix(),
                servletQuery != null ? servletQuery : Map.of(),
                body,
                route.responseStyle());
    }

    private static Optional<LegacyPathAlias> findAlias(LegacyRouteResolver.ResolvedRoute route, HttpMethod method) {
        String prefix = route.mapping().getPathPrefix();
        String legacyPath = normalizeLegacyPath(route.pathAfterPrefix());
        for (LegacyPathAlias alias : BUILTIN_ALIASES) {
            if (!alias.pathPrefix().equals(prefix)) {
                continue;
            }
            if (!alias.legacyPath().equals(legacyPath)) {
                continue;
            }
            if (HttpMethod.POST.equals(method) || !alias.reqBodyToQuery()) {
                return Optional.of(alias);
            }
        }
        return Optional.empty();
    }

    private static String normalizeLegacyPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static LegacyPathAlias alias(
            String prefix,
            String legacyPath,
            String code3rd,
            String method,
            String vendorPath,
            boolean reqBodyToQuery) {
        return new LegacyPathAlias(prefix, legacyPath, code3rd, method, vendorPath, reqBodyToQuery);
    }
}
