/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 控制台运行时信息（供 UI 展示鉴权状态）。
 */
@Schema(description = "控制台运行时信息")
public record ConsoleInfoResponse(
        @Schema(description = "是否启用 API Key 鉴权") boolean securityEnabled,
        @Schema(description = "API Key 请求头名称") String apiKeyHeader) {
}
