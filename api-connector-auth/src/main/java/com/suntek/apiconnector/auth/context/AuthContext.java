/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.auth.context;

import java.util.Map;

/**
 * Authentication context before a single HTTP request.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class AuthContext {

    private final String code3rd;
    private final String baseUrl;
    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final String requestBody;
    private final Map<String, Object> authConfig;
    private final Map<String, String> credentials;
    private final Map<String, Object> ext;

    public AuthContext(
            String code3rd,
            String baseUrl,
            String method,
            String path,
            Map<String, String> query,
            String requestBody,
            Map<String, Object> authConfig,
            Map<String, String> credentials,
            Map<String, Object> ext) {
        this.code3rd = code3rd;
        this.baseUrl = baseUrl;
        this.method = method;
        this.path = path;
        this.query = query;
        this.requestBody = requestBody;
        this.authConfig = authConfig;
        this.credentials = credentials;
        this.ext = ext;
    }

    public String code3rd() {
        return code3rd;
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

    public String requestBody() {
        return requestBody;
    }

    public Map<String, Object> authConfig() {
        return authConfig;
    }

    public Map<String, String> credentials() {
        return credentials;
    }

    public Map<String, Object> ext() {
        return ext;
    }
}
