/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

import java.util.Map;

/**
 * Resolves connector-level auth vs Groovy-only endpoint {@code authOverride}.
 */
public final class AuthConfigResolver {

    private AuthConfigResolver() {
    }

    /**
     * Returns endpoint {@code authOverride} when present; otherwise connector {@code auth}.
     *
     * @param spec     connector specification
     * @param endpoint resolved endpoint, or null for free path invocations
     * @return auth configuration map for {@link com.suntek.apiconnector.auth.context.AuthContext}
     */
    public static Map<String, Object> resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        if (endpoint != null && endpoint.authOverride() != null) {
            return endpoint.authOverride();
        }
        return spec.auth();
    }
}
