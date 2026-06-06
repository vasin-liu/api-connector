/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine.transport;

import java.util.Map;

/**
 * HTTP transport response.
 */
public final class HttpTransportResponse {

    private final int statusCode;
    private final String body;
    private final String bodyEncoding;
    private final Map<String, String> headers;

    public HttpTransportResponse(int statusCode, String body, Map<String, String> headers) {
        this(statusCode, body, null, headers);
    }

    public HttpTransportResponse(int statusCode, String body, String bodyEncoding, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.bodyEncoding = bodyEncoding;
        this.headers = headers;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    public String bodyEncoding() {
        return bodyEncoding;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
