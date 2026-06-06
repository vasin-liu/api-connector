/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.engine.transport;

import java.util.Map;

/**
 * HTTP transport request.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class HttpTransportRequest {

    private final String baseUrl;
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final String body;
    private final Map<String, Object> transportOptions;

    public HttpTransportRequest(
            String baseUrl,
            String method,
            String path,
            Map<String, String> query,
            Map<String, String> headers,
            String body,
            Map<String, Object> transportOptions) {
        this.baseUrl = baseUrl;
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
        this.transportOptions = transportOptions;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> query() {
        return query;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    public Map<String, Object> transportOptions() {
        return transportOptions;
    }
}
