/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.openapi;

import com.suntek.integration.spec.catalog.EndpointDocumentation;
import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointDocSpec;
import com.suntek.integration.spec.model.EndpointParamSpec;
import com.suntek.integration.spec.model.EndpointSpec;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 Spec 端点推导 OpenAPI 展示用 summary / group / 示例参数。
 */
public final class EndpointOpenApiMetadataResolver {

    private EndpointOpenApiMetadataResolver() {
    }

    public static String connectorTag(String code3rd) {
        return "Connector · " + code3rd;
    }

    public static String subgroupTag(String code3rd, String group) {
        return code3rd + " · " + group;
    }

    public static String invokePath(String code3rd, String endpointId) {
        return "/api/v1/integrations/" + code3rd + "/endpoints/" + endpointId + "/invoke";
    }

    public static String operationId(String code3rd, String endpointId) {
        return code3rd + "_" + endpointId;
    }

    public static String resolveSummary(EndpointSpec endpoint) {
        EndpointDocSpec doc = docOf(endpoint);
        if (doc.summary() != null && !doc.summary().isBlank()) {
            return doc.summary();
        }
        return EndpointDocumentation.humanizeId(endpoint.id());
    }

    public static String resolveGroup(EndpointSpec endpoint) {
        EndpointDocSpec doc = docOf(endpoint);
        if (doc.group() != null && !doc.group().isBlank()) {
            return doc.group();
        }
        return EndpointDocumentation.inferGroupFromPath(endpoint.path());
    }

    public static String resolveDescription(ConnectorSpec spec, EndpointSpec endpoint) {
        EndpointDocSpec doc = docOf(endpoint);
        StringBuilder sb = new StringBuilder();
        if (doc.description() != null && !doc.description().isBlank()) {
            sb.append(doc.description().trim());
        } else {
            sb.append("调用厂家 **").append(spec.code3rd()).append("** 接口 `")
                    .append(endpoint.method()).append(' ').append(endpoint.path()).append('`');
        }
        sb.append("\n\n| 项 | 值 |\n|---|---|\n");
        sb.append("| endpointId | `").append(endpoint.id()).append("` |\n");
        sb.append("| 厂家 method | `").append(endpoint.method()).append("` |\n");
        sb.append("| 厂家 path | `").append(endpoint.path()).append("` |\n");
        if (spec.baseUrl() != null) {
            sb.append("| baseUrl | `").append(spec.baseUrl()).append("` |\n");
        }
        return sb.toString();
    }

    public static Map<String, Object> buildExampleRequest(EndpointSpec endpoint) {
        EndpointDocSpec doc = docOf(endpoint);
        Map<String, String> query = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        String body = endpoint.bodyTemplate();
        for (EndpointParamSpec param : doc.parameters()) {
            if (param.name() == null || param.name().isBlank()) {
                continue;
            }
            String in = param.in() == null ? "query" : param.in().toLowerCase(Locale.ROOT);
            String value = param.example() != null ? param.example() : "";
            switch (in) {
                case "header" -> headers.put(param.name(), value);
                case "body" -> body = value;
                default -> query.put(param.name(), value);
            }
        }
        Map<String, Object> example = new LinkedHashMap<>();
        if (!query.isEmpty()) {
            example.put("query", query);
        }
        if (!headers.isEmpty()) {
            example.put("headers", headers);
        }
        if (body != null && !body.isBlank()) {
            example.put("body", body);
        }
        return example;
    }

    public static Set<String> collectSubgroupTags(ConnectorSpec spec) {
        Set<String> groups = new LinkedHashSet<>();
        if (spec.endpoints() == null) {
            return groups;
        }
        for (EndpointSpec endpoint : spec.endpoints()) {
            if (endpoint.enabled() != null && !endpoint.enabled()) {
                continue;
            }
            groups.add(subgroupTag(spec.code3rd(), resolveGroup(endpoint)));
        }
        return groups;
    }

    private static EndpointDocSpec docOf(EndpointSpec endpoint) {
        EndpointSpec enriched = EndpointDocumentation.enrich(endpoint);
        return enriched.doc();
    }
}
