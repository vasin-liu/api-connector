/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.cache;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central in-memory OAuth token cache with proactive refresh and single-flight per key.
 */
public final class TokenCache {

    static final int REFRESH_SKEW_SECONDS = 60;

    private final ConcurrentHashMap<TokenCacheKey, CachedToken> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TokenCacheKey, Object> locks = new ConcurrentHashMap<>();

    /**
     * Returns a valid cached token or refreshes via {@code fetcher} with single-flight per key.
     */
    public CachedToken getOrRefresh(TokenCacheKey key, Supplier<CachedToken> fetcher) {
        CachedToken cached = store.get(key);
        if (cached != null && isValid(cached)) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = store.get(key);
                if (cached != null && isValid(cached)) {
                    return cached;
                }
                CachedToken fresh = fetcher.get();
                store.put(key, fresh);
                return fresh;
            } finally {
                locks.remove(key);
            }
        }
    }

    /**
     * Evicts all cached tokens for the given connector (D-21).
     */
    public void evictForConnector(String code3rd) {
        store.keySet().removeIf(key -> code3rd.equals(key.code3rd()));
    }

    private static boolean isValid(CachedToken token) {
        return Instant.now().isBefore(token.expiresAt().minusSeconds(REFRESH_SKEW_SECONDS));
    }
}
