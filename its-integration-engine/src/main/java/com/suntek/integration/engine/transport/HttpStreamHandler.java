/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine.transport;

import java.util.Map;

/**
 * 流式 HTTP 响应回调（按行或块透传，用于 SSE / NDJSON）。
 */
@FunctionalInterface
public interface HttpStreamHandler {

    /**
     * @param statusCode 厂家 HTTP 状态
     * @param headers    响应头
     * @param line       一行文本（不含换行）；流结束时会收到 {@code null}
     */
    void onLine(int statusCode, Map<String, String> headers, String line);
}
