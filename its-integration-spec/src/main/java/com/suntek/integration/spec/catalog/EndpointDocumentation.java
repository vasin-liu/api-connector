/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.spec.catalog;

import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointDocSpec;
import com.suntek.integration.spec.model.EndpointParamSpec;
import com.suntek.integration.spec.model.EndpointSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从端点 id / path / method 自动推导 OpenAPI 文档（无需 YAML doc 块）。
 */
public final class EndpointDocumentation {

    private static final Pattern CAMEL = Pattern.compile("([a-z])([A-Z])");
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)}");

    private EndpointDocumentation() {
    }

    public static ConnectorSpec enrich(ConnectorSpec spec) {
        if (spec.endpoints() == null || spec.endpoints().isEmpty()) {
            return spec;
        }
        List<EndpointSpec> endpoints = spec.endpoints().stream()
                .map(EndpointDocumentation::enrich)
                .toList();
        return new ConnectorSpec(
                spec.code3rd(),
                spec.version(),
                spec.baseUrl(),
                spec.protocol(),
                spec.auth(),
                endpoints,
                spec.response(),
                spec.transport(),
                spec.transform());
    }

    public static EndpointSpec enrich(EndpointSpec endpoint) {
        if (endpoint.doc() != null) {
            return endpoint;
        }
        EndpointDocSpec doc = new EndpointDocSpec(
                humanizeId(endpoint.id()),
                null,
                inferGroupFromPath(endpoint.path()),
                inferPathParams(endpoint.path()));
        return new EndpointSpec(
                endpoint.id(),
                endpoint.method(),
                endpoint.path(),
                endpoint.bodyTemplate(),
                endpoint.enabled(),
                doc);
    }

    public static String humanizeId(String id) {
        if (id == null || id.isBlank()) {
            return "Invoke";
        }
        String spaced = CAMEL.matcher(id).replaceAll("$1 $2");
        spaced = spaced.replace('_', ' ').replace('-', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    public static String inferGroupFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "其它";
        }
        if (path.contains("/road-aware/")) {
            return "路况感知 · 道路";
        }
        if (path.contains("/macro-aware/")) {
            return "宏观态势";
        }
        if (path.contains("/biz-empower/")) {
            return "业务赋能";
        }
        if (path.contains("/traffic/status")) {
            return "路况查询";
        }
        if (path.contains("/place/")) {
            return "地点服务";
        }
        if (path.contains("/direction")) {
            return "路线规划";
        }
        if (path.contains("/geocod")) {
            return "地理编码";
        }
        if (path.contains("/congestion/")) {
            return "拥堵分析";
        }
        if (path.contains("/index/")) {
            return "交通指数";
        }
        if (path.contains("/event/")) {
            return "交通事件";
        }
        if (path.contains("/predict/")) {
            return "预测";
        }
        if (path.contains("/wenxin") || path.contains("/chat/") || path.contains("/embedding")) {
            return "大模型";
        }
        return "其它";
    }

    public static List<EndpointParamSpec> inferPathParams(String path) {
        List<EndpointParamSpec> params = new ArrayList<>();
        if (path == null) {
            return params;
        }
        Matcher matcher = PATH_PARAM.matcher(path);
        while (matcher.find()) {
            params.add(new EndpointParamSpec(matcher.group(1), "Path 参数", true, "", "path"));
        }
        return params;
    }
}
