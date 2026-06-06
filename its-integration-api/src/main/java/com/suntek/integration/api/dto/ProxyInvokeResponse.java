/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 统一代理调用响应体。
 */
@Data
@Builder
@Schema(description = "第三方调用结果。success 由 Connector Spec response 规则判定；vendorHttpStatus 为厂家原始 HTTP 状态。")
public class ProxyInvokeResponse {

    @Schema(description = "按 Spec response.successWhen 判定是否业务成功")
    private boolean success;

    @Schema(description = "厂家 HTTP 状态码", example = "200")
    private int vendorHttpStatus;

    @Schema(description = "与 vendorHttpStatus 相同（兼容字段）")
    private int httpStatus;

    @Schema(description = "厂家业务码（预留）")
    private String vendorCode;

    @Schema(description = "厂家错误信息（预留）")
    private String vendorMessage;

    @Schema(description = "解析后的业务数据（预留）")
    private Object data;

    @Schema(description = "厂家原始响应体")
    private String rawBody;

    @Schema(description = "rawBody 编码：空=文本，base64=二进制")
    private String rawBodyEncoding;

    @Schema(description = "平台侧耗时（毫秒）")
    private long latencyMillis;

    @Schema(description = "实际命中的 Spec 端点 id，自由 path 调用时为 null")
    private String endpointId;

    @Schema(description = "实际 HTTP 方法")
    private String method;

    @Schema(description = "实际厂家 path")
    private String path;
}
