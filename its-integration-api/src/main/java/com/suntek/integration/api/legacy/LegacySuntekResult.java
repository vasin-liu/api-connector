/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 兼容 {@code com.suntek.common.core.base.Result} 的 JSON 结构。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacySuntekResult {

    private String code;
    private String message;
    private Object data;
    private Boolean success;
}
