/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.auth.profile;

import com.suntek.integration.auth.context.AuthContext;
import com.suntek.integration.auth.context.AuthOutcome;
import com.suntek.integration.auth.spi.AuthProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 高德交通 API 鉴权：clientKey + timestamp + digest（HMAC-SHA256）。
 */
public class GaodeTrafficHmacAuthProvider implements AuthProvider {

    private static final String TYPE = "gaode_traffic_hmac_v1";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String DEFAULT_CLIENT_KEY_REF = "publicKey";
    private static final String DEFAULT_SECRET_REF = "appSecret";

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String clientKeyRef = stringConfig(context.authConfig(), "clientKeyRef", DEFAULT_CLIENT_KEY_REF);
        String secretRef = stringConfig(context.authConfig(), "secretKeyRef", DEFAULT_SECRET_REF);
        String clientKey = requireCredential(context.credentials(), clientKeyRef);
        String secret = requireCredential(context.credentials(), secretRef);
        String timestamp = String.valueOf(System.currentTimeMillis());

        Map<String, String> query = new HashMap<>(context.query());
        query.put("clientKey", clientKey);
        query.put("timestamp", timestamp);

        String digest;
        if ("POST".equalsIgnoreCase(context.method())) {
            digest = hmacHex(secret, clientKey + timestamp);
        } else {
            TreeMap<String, String> sorted = new TreeMap<>(query);
            String paramValuesStr = sorted.values().stream().collect(Collectors.joining());
            digest = hmacHex(secret, paramValuesStr);
        }
        query.put("digest", digest);
        return new AuthOutcome(Map.of(), query, null);
    }

    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Gaode traffic HMAC failed", ex);
        }
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
