/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.domain.model;

import java.util.Map;

/**
 * Result of a third-party invocation (raw body retained for troubleshooting).
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class InvocationResult {

    private final int httpStatus;
    private final boolean success;
    private final String vendorCode;
    private final String vendorMessage;
    private final String rawBody;
    /** {@code base64} 表示 rawBody 为二进制内容的 Base64 编码 */
    private final String rawBodyEncoding;
    private final Object parsedData;
    private final long latencyMillis;
    private final Map<String, String> responseHeaders;

    public InvocationResult(
            int httpStatus,
            boolean success,
            String vendorCode,
            String vendorMessage,
            String rawBody,
            Object parsedData,
            long latencyMillis,
            Map<String, String> responseHeaders) {
        this(httpStatus, success, vendorCode, vendorMessage, rawBody, null, parsedData, latencyMillis, responseHeaders);
    }

    public InvocationResult(
            int httpStatus,
            boolean success,
            String vendorCode,
            String vendorMessage,
            String rawBody,
            String rawBodyEncoding,
            Object parsedData,
            long latencyMillis,
            Map<String, String> responseHeaders) {
        this.httpStatus = httpStatus;
        this.success = success;
        this.vendorCode = vendorCode;
        this.vendorMessage = vendorMessage;
        this.rawBody = rawBody;
        this.rawBodyEncoding = rawBodyEncoding;
        this.parsedData = parsedData;
        this.latencyMillis = latencyMillis;
        this.responseHeaders = responseHeaders;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean success() {
        return success;
    }

    public String vendorCode() {
        return vendorCode;
    }

    public String vendorMessage() {
        return vendorMessage;
    }

    public String rawBody() {
        return rawBody;
    }

    public String rawBodyEncoding() {
        return rawBodyEncoding;
    }

    public Object parsedData() {
        return parsedData;
    }

    public long latencyMillis() {
        return latencyMillis;
    }

    public Map<String, String> responseHeaders() {
        return responseHeaders;
    }
}
