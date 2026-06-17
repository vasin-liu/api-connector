/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 将流式行写入 Servlet {@link OutputStream}（SSE 透传）。
 */
public class ServletStreamingInvocationSink implements StreamingInvocationSink {

    private final OutputStream outputStream;

    public ServletStreamingInvocationSink(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void writeLine(String line) {
        try {
            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
            outputStream.write('\n');
            outputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write stream line", e);
        }
    }

    @Override
    public void onVendorResponse(int httpStatus, Map<String, String> headers) {
        // 平台层保持 200 + text/event-stream；厂家状态不写入 SSE 体
    }

    @Override
    public void complete() {
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to flush stream", e);
        }
    }
}
