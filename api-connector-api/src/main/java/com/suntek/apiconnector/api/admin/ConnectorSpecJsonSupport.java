/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.api.admin;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.ResponseSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ConnectorSpec 与 JSON Map 互转（供管理 API / UI 使用）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ConnectorSpecJsonSupport {

    private ConnectorSpecJsonSupport() {
    }

    /**
     * 将 Spec 转为可序列化 Map。
     *
     * @param spec 连接器规格
     * @return Map 结构
     */
    public static Map<String, Object> toMap(ConnectorSpec spec) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("code3rd", spec.code3rd());
        root.put("version", spec.version());
        root.put("baseUrl", spec.baseUrl());
        root.put("protocol", spec.protocol());
        root.put("auth", spec.auth());
        root.put("endpoints", spec.endpoints() == null
                ? List.of()
                : spec.endpoints().stream().map(ConnectorSpecJsonSupport::endpointToMap).toList());
        root.put("response", responseToMap(spec.response()));
        root.put("transport", spec.transport());
        root.put("transform", spec.transform());
        return root;
    }

    /**
     * 从 auth 配置解析主 Profile 类型摘要。
     *
     * @param auth auth 节点
     * @return Profile 类型或 pipeline 摘要
     */
    @SuppressWarnings("unchecked")
    public static String resolveAuthType(Map<String, Object> auth) {
        if (auth == null || auth.isEmpty()) {
            return "none";
        }
        Object pipeline = auth.get("pipeline");
        if (pipeline instanceof List<?> steps && !steps.isEmpty()) {
            StringBuilder sb = new StringBuilder("pipeline:");
            for (Object step : steps) {
                if (step instanceof Map<?, ?> map && map.get("type") != null) {
                    if (sb.length() > 9) {
                        sb.append('+');
                    }
                    sb.append(map.get("type"));
                }
            }
            return sb.toString();
        }
        Object type = auth.get("type");
        return type == null ? "unknown" : String.valueOf(type);
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

    private static Map<String, Object> responseToMap(ResponseSpec response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("successWhen", response.successWhen());
        map.put("dataPath", response.dataPath());
        map.put("messagePath", response.messagePath());
        map.put("codePath", response.codePath());
        return map;
    }
}
