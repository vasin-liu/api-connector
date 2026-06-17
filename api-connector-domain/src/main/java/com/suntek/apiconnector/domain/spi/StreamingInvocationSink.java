/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.domain.spi;

import java.util.Map;

/**
 * 流式调用输出端（平台 → 客户端）。
 */
public interface StreamingInvocationSink {

    /**
     * 写入一行（SSE 场景下通常为 {@code data: ...} 或厂家原始行）。
     *
     * @param line 文本行，不含换行
     */
    void writeLine(String line);

    /**
     * 厂家 HTTP 状态与响应头（在首行之前或首行时调用一次）。
     */
    default void onVendorResponse(int httpStatus, Map<String, String> headers) {
    }

    /**
     * 流结束。
     */
    default void complete() {
    }

    /**
     * 流失败。
     */
    default void fail(Throwable error) {
    }
}
