/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.spec.model;

import java.util.List;
import java.util.Map;

/**
 * Declarative connector specification (YAML/DB isomorphic).
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ConnectorSpec {

    private final String code3rd;
    private final String version;
    private final String baseUrl;
    private final String protocol;
    private final Map<String, Object> auth;
    private final List<EndpointSpec> endpoints;
    private final ResponseSpec response;
    private final Map<String, Object> transport;
    private final List<Map<String, Object>> transform;

    public ConnectorSpec(
            String code3rd,
            String version,
            String baseUrl,
            String protocol,
            Map<String, Object> auth,
            List<EndpointSpec> endpoints,
            ResponseSpec response,
            Map<String, Object> transport,
            List<Map<String, Object>> transform) {
        this.code3rd = code3rd;
        this.version = version;
        this.baseUrl = baseUrl;
        this.protocol = protocol;
        this.auth = auth;
        this.endpoints = endpoints;
        this.response = response;
        this.transport = transport;
        this.transform = transform;
    }

    public String code3rd() {
        return code3rd;
    }

    public String version() {
        return version;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String protocol() {
        return protocol;
    }

    public Map<String, Object> auth() {
        return auth;
    }

    public List<EndpointSpec> endpoints() {
        return endpoints;
    }

    public ResponseSpec response() {
        return response;
    }

    public Map<String, Object> transport() {
        return transport;
    }

    public List<Map<String, Object>> transform() {
        return transform;
    }
}
