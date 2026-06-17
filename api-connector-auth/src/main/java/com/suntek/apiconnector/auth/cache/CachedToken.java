/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.cache;

import java.time.Instant;

/**
 * Cached OAuth access token with expiry and optional raw token response body.
 */
public record CachedToken(String accessToken, Instant expiresAt, String rawResponse) {

    public CachedToken(String accessToken, Instant expiresAt) {
        this(accessToken, expiresAt, null);
    }
}
