/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.profile;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.context.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * 静态 Bearer Token（TransBridge、ChatGPT 等）。
 */
public class BearerStaticAuthProvider implements AuthProvider {

    private static final String TYPE = "bearer_static";
    private static final String DEFAULT_TOKEN_REF = "appSecret";

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String tokenRef = stringConfig(context.authConfig(), "tokenRef", DEFAULT_TOKEN_REF);
        String token = requireCredential(context.credentials(), tokenRef);
        String headerName = stringConfig(context.authConfig(), "headerName", "Authorization");
        String prefix = stringConfig(context.authConfig(), "prefix", "Bearer ");
        Map<String, String> headers = new HashMap<>();
        headers.put(headerName, prefix + token);
        return new AuthOutcome(headers, Map.of(), null);
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
