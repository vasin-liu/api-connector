/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.spec;

import com.suntek.apiconnector.spec.catalog.EndpointDocumentation;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingRule;
import com.suntek.apiconnector.spec.model.MappingSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;

import java.util.ArrayList;
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
                parseMappingSpec(map(connector.get("mapping")), "mapping"),
                list(connector.get("transform"))));
    }

    private static EndpointSpec toEndpoint(Map<String, Object> m) {
        Map<String, Object> authOverride = map(m.get("authOverride"));
        if (!authOverride.isEmpty()) {
            validateAuthOverride(authOverride);
        }
        MappingSpec mappingOverride = parseMappingSpec(map(m.get("mappingOverride")), "mappingOverride");
        EndpointSpec endpoint = new EndpointSpec(
                str(m.get("id")),
                str(m.get("method")),
                str(m.get("path")),
                str(m.get("bodyTemplate")),
                m.get("enabled") == null || Boolean.TRUE.equals(m.get("enabled")),
                null,
                authOverride.isEmpty() ? null : authOverride,
                mappingOverride);
        return EndpointDocumentation.enrich(endpoint);
    }

    private static void validateAuthOverride(Map<String, Object> authOverride) {
        Object type = authOverride.get("type");
        if (!"groovy_auth_script".equals(String.valueOf(type))) {
            throw new IllegalArgumentException(
                    "Endpoint authOverride is Groovy-only: type must be groovy_auth_script, got: " + type);
        }
    }

    private static MappingSpec parseMappingSpec(Map<String, Object> mappingMap, String pathPrefix) {
        if (mappingMap.isEmpty()) {
            return null;
        }
        DirectionMappingSpec request = parseDirectionMapping(
                map(mappingMap.get("request")), pathPrefix + ".request");
        DirectionMappingSpec response = parseDirectionMapping(
                map(mappingMap.get("response")), pathPrefix + ".response");
        DirectionMappingSpec error = parseDirectionMapping(
                map(mappingMap.get("error")), pathPrefix + ".error");
        if (request == null && response == null && error == null) {
            return null;
        }
        return new MappingSpec(request, response, error);
    }

    private static DirectionMappingSpec parseDirectionMapping(Map<String, Object> directionMap, String directionPath) {
        if (directionMap.isEmpty()) {
            return null;
        }
        List<MappingRule> rules = parseMappingRules(list(directionMap.get("rules")));
        String script = str(directionMap.get("script"));
        boolean hasRules = rules != null && !rules.isEmpty();
        boolean hasScript = script != null && !script.isBlank();
        if (hasRules && hasScript) {
            throw new IllegalArgumentException(
                    directionPath + " cannot contain both rules and script (D-10)");
        }
        if (!hasRules && !hasScript) {
            return null;
        }
        return new DirectionMappingSpec(hasRules ? rules : null, hasScript ? script : null);
    }

    private static List<MappingRule> parseMappingRules(List<Map<String, Object>> ruleMaps) {
        if (ruleMaps == null || ruleMaps.isEmpty()) {
            return null;
        }
        List<MappingRule> rules = new ArrayList<>();
        for (Map<String, Object> ruleMap : ruleMaps) {
            List<MappingRule> nested = parseMappingRules(list(ruleMap.get("rules")));
            rules.add(new MappingRule(
                    str(ruleMap.get("op")),
                    str(ruleMap.get("source")),
                    str(ruleMap.get("target")),
                    str(ruleMap.get("type")),
                    ruleMap.get("value"),
                    nested));
        }
        return rules;
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
        if (spec.mapping() != null) {
            Map<String, Object> mapping = mappingToMap(spec.mapping());
            if (!mapping.isEmpty()) {
                connector.put("mapping", mapping);
            }
        }
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
        if (endpoint.authOverride() != null && !endpoint.authOverride().isEmpty()) {
            map.put("authOverride", endpoint.authOverride());
        }
        if (endpoint.mappingOverride() != null) {
            Map<String, Object> mappingOverride = mappingToMap(endpoint.mappingOverride());
            if (!mappingOverride.isEmpty()) {
                map.put("mappingOverride", mappingOverride);
            }
        }
        return map;
    }

    private static Map<String, Object> mappingToMap(MappingSpec mapping) {
        Map<String, Object> result = new LinkedHashMap<>();
        putDirectionIfPresent(result, "request", mapping.request());
        putDirectionIfPresent(result, "response", mapping.response());
        putDirectionIfPresent(result, "error", mapping.error());
        return result;
    }

    private static void putDirectionIfPresent(
            Map<String, Object> parent, String key, DirectionMappingSpec direction) {
        if (direction == null) {
            return;
        }
        Map<String, Object> directionMap = directionMappingToMap(direction);
        if (!directionMap.isEmpty()) {
            parent.put(key, directionMap);
        }
    }

    private static Map<String, Object> directionMappingToMap(DirectionMappingSpec direction) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (direction.rules() != null && !direction.rules().isEmpty()) {
            map.put("rules", direction.rules().stream()
                    .map(ConnectorSpecParser::mappingRuleToMap)
                    .collect(Collectors.toList()));
        }
        if (direction.script() != null && !direction.script().isBlank()) {
            map.put("script", direction.script());
        }
        return map;
    }

    private static Map<String, Object> mappingRuleToMap(MappingRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("op", rule.op());
        if (rule.source() != null) {
            map.put("source", rule.source());
        }
        if (rule.target() != null) {
            map.put("target", rule.target());
        }
        if (rule.type() != null) {
            map.put("type", rule.type());
        }
        if (rule.value() != null) {
            map.put("value", rule.value());
        }
        if (rule.rules() != null && !rule.rules().isEmpty()) {
            map.put("rules", rule.rules().stream()
                    .map(ConnectorSpecParser::mappingRuleToMap)
                    .collect(Collectors.toList()));
        }
        return map;
    }

    private static String str(Object o) {
        return Objects.toString(o, null);
    }
}
