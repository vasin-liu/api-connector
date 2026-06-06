/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Connector Spec 端点目录项（OpenAPI / 控制台试调）。
 */
@Data
@Builder
@Schema(description = "已登记端点摘要")
public class ConnectorEndpointSummary {

    @Schema(description = "端点 id，用于 invoke", example = "roadSpeeds")
    private String id;

    @Schema(description = "HTTP 方法", example = "GET")
    private String method;

    @Schema(description = "厂家 path", example = "/api/v2/traffic-aware/road-aware/speeds")
    private String path;

    @Schema(description = "可读标题（OpenAPI summary）", example = "路段速度")
    private String summary;

    @Schema(description = "业务分组", example = "路况感知 · 道路")
    private String group;

    @Schema(description = "是否启用")
    private boolean enabled;

    @Schema(
            description = "推荐调用 URL（平台侧）",
            example = "/api/v1/integrations/IDPS/endpoints/roadSpeeds/invoke")
    private String invokeUrl;
}
