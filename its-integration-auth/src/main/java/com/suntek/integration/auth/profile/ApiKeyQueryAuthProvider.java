/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.auth.profile;

import com.suntek.integration.auth.context.AuthContext;
import com.suntek.integration.auth.context.AuthOutcome;
import com.suntek.integration.auth.spi.AuthProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * API Key 作为 Query 参数（高德等）。
 */
public class ApiKeyQueryAuthProvider implements AuthProvider {

    private static final String TYPE = "api_key_query";
    private static final String DEFAULT_KEY_REF = "appSecret";
    private static final String DEFAULT_PARAM_NAME = "key";

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String keyRef = stringConfig(context.authConfig(), "keyRef", DEFAULT_KEY_REF);
        String paramName = stringConfig(context.authConfig(), "paramName", DEFAULT_PARAM_NAME);
        String apiKey = requireCredential(context.credentials(), keyRef);
        Map<String, String> query = new HashMap<>(context.query());
        query.put(paramName, apiKey);
        return new AuthOutcome(Map.of(), query, null);
    }

    private static String stringConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String requireCredential(Map<String, String> credentials, String ref) {
        String value = credentials.get(ref);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing credential for ref: " + ref);
        }
        return value;
    }
}
