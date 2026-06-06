/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 按 endpointId 调用时的精简请求体（path/method 由 Spec 决定）。
 */
@Data
@Schema(description = "按 Spec 端点 id 调用，仅需业务 query/body。")
public class EndpointInvokeRequest {

    @Schema(description = "Query 参数")
    private Map<String, String> query;

    @Schema(description = "额外请求头")
    private Map<String, String> headers;

    @Schema(description = "请求体 JSON 字符串")
    private String body;
}
