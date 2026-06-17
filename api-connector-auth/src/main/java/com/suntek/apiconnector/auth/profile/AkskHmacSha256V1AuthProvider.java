/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.auth.profile;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthProvider;
import com.suntek.apiconnector.auth.support.AkskCanonicalSigner;

import java.util.HashMap;
import java.util.Map;

/**
 * AK/SK canonical HMAC-SHA256 authentication (IDPS / Traffic brain style).
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public class AkskHmacSha256V1AuthProvider implements AuthProvider {

    private static final String TYPE = "aksk_hmac_sha256_v1";
    private static final String DEFAULT_ACCESS_KEY_REF = "publicKey";
    private static final String DEFAULT_SECRET_KEY_REF = "appSecret";

    /**
     * {@inheritDoc}
     */
    @Override
    public String profileType() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthOutcome apply(AuthContext context) {
        String accessKeyRef = stringConfig(context.authConfig(), "accessKeyRef", DEFAULT_ACCESS_KEY_REF);
        String secretKeyRef = stringConfig(context.authConfig(), "secretKeyRef", DEFAULT_SECRET_KEY_REF);

        String accessKey = requireCredential(context.credentials(), accessKeyRef);
        String secretKey = requireCredential(context.credentials(), secretKeyRef);

        String uri = context.path() != null && context.path().startsWith("/")
                ? context.path()
                : "/" + (context.path() != null ? context.path() : "");

        Map<String, String> signedHeaders = AkskCanonicalSigner.sign(
                context.method(),
                uri,
                context.query(),
                accessKey,
                secretKey);

        return new AuthOutcome(new HashMap<>(signedHeaders), Map.of(), null);
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
