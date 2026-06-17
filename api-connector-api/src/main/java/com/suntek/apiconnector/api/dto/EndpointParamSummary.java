/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 端点参数摘要（试调表单 / OpenAPI）。
 */
@Data
@Builder
@Schema(description = "端点参数说明")
public class EndpointParamSummary {

    @Schema(description = "参数名", example = "adcode")
    private String name;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "示例值", example = "440100")
    private String example;

    @Schema(description = "参数位置：query / header / body", example = "query")
    private String in;
}
