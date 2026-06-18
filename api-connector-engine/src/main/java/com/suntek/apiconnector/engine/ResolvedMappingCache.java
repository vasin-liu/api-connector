/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of {@link ResolvedMapping} keyed by {@code code3rd:endpointId} (D-21).
 *
 * <p>Amortizes {@link MappingConfigResolver#resolve(ConnectorSpec, EndpointSpec)} so a mapped
 * endpoint resolves its merged connector-default plus endpoint-override config once and reuses
 * the result on every subsequent invoke. Passthrough endpoints stay zero-overhead because the
 * orchestrator gates execution on {@link MappingConfigResolver#hasAnyMapping(ResolvedMapping)}.
 * Entries are invalidated by {@link #evict(String)} from {@code ConnectorPublishListener} on
 * connector publish, mirroring the token-cache invalidation lifecycle.</p>
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-18
 */
public final class ResolvedMappingCache {

    private final Map<String, ResolvedMapping> cache = new ConcurrentHashMap<>();

    /**
     * Returns the resolved mapping for a connector endpoint, resolving and caching on first use.
     *
     * @param spec     connector spec, must not be {@code null}
     * @param endpoint endpoint spec, must not be {@code null}
     * @return the cached or freshly resolved {@link ResolvedMapping} for this connector+endpoint
     */
    public ResolvedMapping get(ConnectorSpec spec, EndpointSpec endpoint) {
        String key = spec.code3rd() + ":" + endpoint.id();
        return cache.computeIfAbsent(key, k -> MappingConfigResolver.resolve(spec, endpoint));
    }

    /**
     * Evicts all cached entries for a connector, forcing the next invoke to re-resolve (D-21).
     *
     * @param code3rd connector code whose cached entries are dropped; {@code null} is a no-op
     */
    public void evict(String code3rd) {
        if (code3rd == null) {
            return;
        }
        String prefix = code3rd + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
