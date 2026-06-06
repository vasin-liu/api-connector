/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.domain.model;

import java.util.Map;

/**
 * A single outbound invocation to a third-party system.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class InvocationRequest {

    private final ConnectorCode connectorCode;
    private final String endpointId;
    private final HttpMethod method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final String body;
    private final InvocationMode mode;

    /**
     * Creates an invocation request.
     *
     * @param connectorCode connector id
     * @param endpointId    optional endpoint id from spec
     * @param method        HTTP method
     * @param path          request path
     * @param query         query parameters
     * @param headers       request headers
     * @param body          request body
     * @param mode          invocation mode
     */
    public InvocationRequest(
            ConnectorCode connectorCode,
            String endpointId,
            HttpMethod method,
            String path,
            Map<String, String> query,
            Map<String, String> headers,
            String body,
            InvocationMode mode) {
        this.connectorCode = connectorCode;
        this.endpointId = endpointId;
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
        this.body = body;
        this.mode = mode;
    }

    public ConnectorCode connectorCode() {
        return connectorCode;
    }

    public String endpointId() {
        return endpointId;
    }

    public HttpMethod method() {
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

    public InvocationMode mode() {
        return mode;
    }

    /**
     * HTTP method.
     */
    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }

    /**
     * Invocation mode.
     */
    public enum InvocationMode {
        SYNC, SSE, STREAM_ASYNC
    }
}
