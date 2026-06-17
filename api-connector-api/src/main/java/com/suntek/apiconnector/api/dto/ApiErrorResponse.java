/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台 API 错误响应。
 */
@Data
@Builder
@Schema(description = "错误响应")
public class ApiErrorResponse {

    @Schema(description = "错误码", example = "CONNECTOR_NOT_FOUND")
    private String code;

    @Schema(description = "人类可读说明")
    private String message;
}
