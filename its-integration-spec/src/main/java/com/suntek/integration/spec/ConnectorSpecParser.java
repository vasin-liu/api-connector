/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.spec;

import com.suntek.integration.spec.catalog.EndpointDocumentation;
import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointSpec;
import com.suntek.integration.spec.model.ResponseSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从 JSON/YAML 同构 Map 解析 {@link ConnectorSpec}。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ConnectorSpecParser {

    private ConnectorSpecParser() {
    }

    /**
     * 解析 Spec Map（顶层含 {@code code3rd}，或包裹在 {@code connector} 下）。
     *
     * @param root 规格 Map
     * @return 连接器规格
     */
    @SuppressWarnings("unchecked")
    public static ConnectorSpec parse(Map<String, Object> root) {
        Map<String, Object> connector = root.containsKey("connector")
                ? map(root.get("connector"))
                : root;
        List<Map<String, Object>> endpointMaps = list(connector.get("endpoints"));
        List<EndpointSpec> endpoints = endpointMaps.stream()
                .map(ConnectorSpecParser::toEndpoint)
                .collect(Collectors.toList());
        Map<String, Object> responseMap = map(connector.get("response"));
        ResponseSpec response = responseMap.isEmpty()
                ? null
                : new ResponseSpec(
                str(responseMap.get("successWhen")),
                str(responseMap.get("dataPath")),
                str(responseMap.get("messagePath")),
                str(responseMap.get("codePath")));
        return EndpointDocumentation.enrich(new ConnectorSpec(
                str(connector.get("code3rd")),
                str(connector.getOrDefault("version", "1.0.0")),
                str(connector.get("baseUrl")),
                str(connector.getOrDefault("protocol", "HTTP")),
                map(connector.get("auth")),
                endpoints,
                response,
                map(connector.get("transport")),
                list(connector.get("transform"))));
    }

    private static EndpointSpec toEndpoint(Map<String, Object> m) {
        EndpointSpec endpoint = new EndpointSpec(
                str(m.get("id")),
                str(m.get("method")),
                str(m.get("path")),
                str(m.get("bodyTemplate")),
                m.get("enabled") == null || Boolean.TRUE.equals(m.get("enabled")),
                null);
        return EndpointDocumentation.enrich(endpoint);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        if (o instanceof List) {
            List<?> items = (List<?>) o;
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(x -> (Map<String, Object>) x)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 将 {@link ConnectorSpec} 转为与 YAML/JSON 同构的 connector 节点 Map。
     *
     * @param spec 连接器规格
     * @return connector 节点
     */
    public static Map<String, Object> toConnectorMap(ConnectorSpec spec) {
        Map<String, Object> connector = new LinkedHashMap<>();
        connector.put("code3rd", spec.code3rd());
        connector.put("version", spec.version());
        connector.put("baseUrl", spec.baseUrl());
        connector.put("protocol", spec.protocol());
        connector.put("auth", spec.auth());
        connector.put("endpoints", spec.endpoints() == null
                ? Collections.emptyList()
                : spec.endpoints().stream().map(ConnectorSpecParser::endpointToMap).collect(Collectors.toList()));
        if (spec.response() != null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("successWhen", spec.response().successWhen());
            response.put("dataPath", spec.response().dataPath());
            response.put("messagePath", spec.response().messagePath());
            response.put("codePath", spec.response().codePath());
            connector.put("response", response);
        }
        connector.put("transport", spec.transport());
        connector.put("transform", spec.transform());
        return connector;
    }

    private static Map<String, Object> endpointToMap(EndpointSpec endpoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", endpoint.id());
        map.put("method", endpoint.method());
        map.put("path", endpoint.path());
        map.put("bodyTemplate", endpoint.bodyTemplate());
        map.put("enabled", endpoint.enabled());
        return map;
    }

    private static String str(Object o) {
        return Objects.toString(o, null);
    }
}
