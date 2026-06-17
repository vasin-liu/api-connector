/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suntek.apiconnector.api.dto.ProxyInvokeRequest;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import com.suntek.apiconnector.api.invoke.InvokeContext;
import com.suntek.apiconnector.api.invoke.InvokeHttpResponseMapper;
import com.suntek.apiconnector.api.service.IntegrationInvokeService;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * IDPS 聚合接口与本地签名（对齐 system-thirdpart IdpsInvokeService）。
 */
@Component
public class IdpsLegacySpecialHandler implements LegacySpecialHandler {

    private static final String PREFIX = "/idps";

    private final IntegrationInvokeService invokeService;
    private final LegacyCompatResponseFormatter responseFormatter;
    private final InvokeHttpResponseMapper responseMapper;
    private final ConnectorRegistry registry;
    private final ObjectMapper objectMapper;

    public IdpsLegacySpecialHandler(
            IntegrationInvokeService invokeService,
            LegacyCompatResponseFormatter responseFormatter,
            InvokeHttpResponseMapper responseMapper,
            ConnectorRegistry registry,
            ObjectMapper objectMapper) {
        this.invokeService = invokeService;
        this.responseFormatter = responseFormatter;
        this.responseMapper = responseMapper;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ResponseEntity<String>> tryHandle(
            HttpServletRequest request, LegacyRouteResolver.ResolvedRoute route) {
        if (!PREFIX.equals(route.mapping().getPathPrefix())) {
            return Optional.empty();
        }
        String path = route.pathAfterPrefix();
        return switch (path) {
            case "/roadIndex" -> Optional.of(handleRoadIndex(request));
            case "/districtIndex" -> Optional.of(handleDistrictIndex(request));
            case "/getSign" -> Optional.of(handleGetSign());
            case "/currentUserInfo" -> Optional.of(handleCurrentUserInfo(request));
            default -> Optional.empty();
        };
    }

    private ResponseEntity<String> handleRoadIndex(HttpServletRequest request) {
        Map<String, String> query = ThirdpartLegacyDispatcher.queryParams(request);
        Map<String, ObjectNode> merged = new TreeMap<>(Comparator.comparing(String::valueOf));
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/road-aware/speeds", query),
                "speeds",
                "value");
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/road-aware/congestion-indexes", query),
                "congestionIndexe",
                "congestionIndexe");
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/road-aware/congestion-miles", query),
                "congestionMiles",
                "congestionMiles");
        ArrayNode array = objectMapper.createArrayNode();
        merged.values().forEach(array::add);
        return suntekOk(array);
    }

    private ResponseEntity<String> handleDistrictIndex(HttpServletRequest request) {
        Map<String, String> query = ThirdpartLegacyDispatcher.queryParams(request);
        String domainCode = query.get("domain_code");
        String domainType = query.get("domain_type");
        String fromTime = query.get("from_time");
        String toTime = query.get("to_time");

        Map<String, ObjectNode> merged = new TreeMap<>(Comparator.comparing(String::valueOf));
        Map<String, String> districtQuery = Map.of(
                "district", domainCode != null ? domainCode : "",
                "from_time", fromTime != null ? fromTime : "",
                "to_time", toTime != null ? toTime : "");
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/macro-aware/road-speeds", districtQuery),
                "speeds",
                "roadSpeed");

        Map<String, String> domainQuery = new LinkedHashMap<>();
        domainQuery.put("domain_code", domainCode != null ? domainCode : "");
        domainQuery.put("domain_type", domainType != null ? domainType : "");
        domainQuery.put("from_time", fromTime != null ? fromTime : "");
        domainQuery.put("to_time", toTime != null ? toTime : "");
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/macro-aware/congestion-miles", domainQuery),
                "congestionMiles",
                "congestionMiles");
        mergeArrayByTime(
                merged,
                invokeVendorArray("/api/v2/traffic-aware/macro-aware/congestion-indexes", districtQuery),
                "congestionIndexe",
                "congestionIndexe");

        ArrayNode array = objectMapper.createArrayNode();
        merged.values().forEach(array::add);
        return suntekOk(array);
    }

    private ResponseEntity<String> handleGetSign() {
        Map<String, String> creds = registry.credentials("IDPS");
        String loginName = creds.getOrDefault("loginName", "");
        String key = creds.getOrDefault("key", creds.getOrDefault("publicKey", ""));
        long time = System.currentTimeMillis() / 1000;
        String sign = md5Hex(String.format("loginName=%s&key=%s&time=%s", loginName, key, time));
        ObjectNode data = objectMapper.createObjectNode();
        data.put("userName", loginName);
        data.put("sign", sign);
        data.put("time", time);
        return suntekOk(data);
    }

    private ResponseEntity<String> handleCurrentUserInfo(HttpServletRequest request) {
        String token = request.getParameter("token");
        ProxyInvokeRequest invokeRequest = new ProxyInvokeRequest();
        invokeRequest.setMethod("POST");
        invokeRequest.setPath("/brain-auth/check/getUserInfo");
        invokeRequest.setQuery(token != null ? Map.of("token", token) : Map.of());
        ProxyInvokeResponse body = invokeService.invoke("IDPS", invokeRequest, InvokeContext.LEGACY);
        return format(body, LegacyResponseStyle.SUNTEK_RESULT);
    }

    private JsonNode invokeVendorArray(String path, Map<String, String> query) {
        ProxyInvokeRequest request = new ProxyInvokeRequest();
        request.setMethod("GET");
        request.setPath(path);
        request.setQuery(query);
        ProxyInvokeResponse response = invokeService.invoke("IDPS", request, InvokeContext.LEGACY);
        if (!response.isSuccess() || response.getRawBody() == null) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode root = objectMapper.readTree(response.getRawBody());
            JsonNode obj = root.get("obj");
            if (obj != null && obj.isArray()) {
                return obj;
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createArrayNode();
    }

    private void mergeArrayByTime(
            Map<String, ObjectNode> merged, JsonNode array, String targetField, String sourceField) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            String timeKey = item.path("time").asText("");
            ObjectNode row = merged.computeIfAbsent(timeKey, k -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("time", timeKey);
                return node;
            });
            if (item.has(sourceField)) {
                row.set(targetField, item.get(sourceField));
            } else if (item.has("value")) {
                row.set(targetField, item.get("value"));
            }
        }
    }

    private ResponseEntity<String> suntekOk(JsonNode data) {
        ProxyInvokeResponse body =
                ProxyInvokeResponse.builder().success(true).vendorHttpStatus(200).data(data).build();
        return format(body, LegacyResponseStyle.SUNTEK_RESULT);
    }

    private ResponseEntity<String> format(ProxyInvokeResponse body, LegacyResponseStyle style) {
        String payload = responseFormatter.format(body, style);
        int status = responseMapper.resolveLegacyHttpStatus(body);
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(payload);
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 failed", e);
        }
    }
}
