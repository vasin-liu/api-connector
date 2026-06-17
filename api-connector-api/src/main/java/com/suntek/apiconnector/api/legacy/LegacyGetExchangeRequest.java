/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import java.util.HashMap;
import java.util.Map;

/**
 * 兼容 system-thirdpart {@code POST /idps/getExchange} 请求体。
 */
public class LegacyGetExchangeRequest {

    private String uri;
    private Map<String, String> queryParameters = new HashMap<>();

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters != null ? queryParameters : Map.of();
    }

    public void setQueryParameters(Map<String, String> queryParameters) {
        this.queryParameters = queryParameters;
    }
}
