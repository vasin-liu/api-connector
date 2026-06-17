/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 按 Spec 端点或自由 path 调用厂家 HTTP 接口。
 */
@Data
@Schema(description = "第三方 HTTP 调用请求。推荐仅传 endpointId + query/body；高级场景可传 method + path。")
public class ProxyInvokeRequest {

    @Schema(
            description = "Connector Spec 中登记的端点 id（推荐）。与 method/path 二选一。",
            example = "roadSpeeds")
    private String endpointId;

    @Schema(
            description = "HTTP 方法。endpointId 命中时可省略；否则必填。",
            example = "GET")
    private String method;

    @Schema(
            description = "厂家 API 路径（不含 baseUrl）。endpointId 命中时可省略。",
            example = "/api/v2/traffic-aware/road-aware/speeds")
    @JsonAlias("uri")
    private String path;

    @Schema(description = "Query 参数（会与 Auth Profile 注入的参数合并）")
    private Map<String, String> query;

    @Schema(description = "额外请求头（Auth 头由平台注入）")
    private Map<String, String> headers;

    @Schema(description = "请求体 JSON 字符串")
    private String body;

    /**
     * 兼容旧字段名 {@code uri}。
     *
     * @return path
     */
    public String effectivePath() {
        return path;
    }

    /**
     * 校验：endpointId 与 (method+path) 至少一种完整。
     *
     * @return 是否有效
     */
    public boolean hasTarget() {
        boolean byEndpoint = endpointId != null && !endpointId.isBlank();
        boolean byPath = method != null && !method.isBlank()
                && path != null && !path.isBlank();
        return byEndpoint || byPath;
    }
}
