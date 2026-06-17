package com.suntek.apiconnector.auth.cache;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TokenCacheTest {

    private static final TokenCacheKey KEY = new TokenCacheKey("VENDOR", "oauth2_client_credentials", "read");

    @Test
    void returnsCachedTokenWithinRefreshSkew() {
        TokenCache cache = new TokenCache();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        CachedToken seeded = new CachedToken("token-a", expiresAt);
        cache.getOrRefresh(KEY, () -> seeded);

        AtomicInteger fetchCount = new AtomicInteger();
        CachedToken result = cache.getOrRefresh(KEY, () -> {
            fetchCount.incrementAndGet();
            return new CachedToken("token-b", Instant.now().plusSeconds(3600));
        });

        assertSame(seeded, result);
        assertEquals(0, fetchCount.get());
    }

    @Test
    void refreshesWhenWithinSkewWindow() {
        TokenCache cache = new TokenCache();
        Instant nearExpiry = Instant.now().plusSeconds(30);
        cache.getOrRefresh(KEY, () -> new CachedToken("old-token", nearExpiry));

        AtomicInteger fetchCount = new AtomicInteger();
        CachedToken refreshed = cache.getOrRefresh(KEY, () -> {
            fetchCount.incrementAndGet();
            return new CachedToken("new-token", Instant.now().plusSeconds(3600));
        });

        assertEquals(1, fetchCount.get());
        assertEquals("new-token", refreshed.accessToken());
    }

    @Test
    void evictForConnectorRemovesMatchingEntries() {
        TokenCache cache = new TokenCache();
        TokenCacheKey otherConnector = new TokenCacheKey("OTHER", "oauth2_client_credentials", "");
        cache.getOrRefresh(KEY, () -> new CachedToken("a", Instant.now().plusSeconds(3600)));
        cache.getOrRefresh(otherConnector, () -> new CachedToken("b", Instant.now().plusSeconds(3600)));

        cache.evictForConnector("VENDOR");

        AtomicInteger fetchCount = new AtomicInteger();
        cache.getOrRefresh(KEY, () -> {
            fetchCount.incrementAndGet();
            return new CachedToken("refetched", Instant.now().plusSeconds(3600));
        });
        cache.getOrRefresh(otherConnector, () -> {
            fetchCount.incrementAndGet();
            return new CachedToken("still-cached", Instant.now().plusSeconds(3600));
        });

        assertEquals(1, fetchCount.get());
    }

    @Test
    void concurrentGetOrRefreshFetchesOnce() throws InterruptedException {
        TokenCache cache = new TokenCache();
        AtomicInteger fetchCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        cache.getOrRefresh(KEY, () -> {
                            fetchCount.incrementAndGet();
                            return new CachedToken("shared", Instant.now().plusSeconds(3600));
                        });
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, fetchCount.get());
    }

    @Test
    void cachedTokenSupportsOptionalRawResponse() {
        CachedToken token = new CachedToken("t", Instant.now().plusSeconds(60), "{\"access_token\":\"t\"}");
        assertEquals("{\"access_token\":\"t\"}", token.rawResponse());

        CachedToken withoutRaw = new CachedToken("t", Instant.now().plusSeconds(60));
        assertNull(withoutRaw.rawResponse());
    }
}
