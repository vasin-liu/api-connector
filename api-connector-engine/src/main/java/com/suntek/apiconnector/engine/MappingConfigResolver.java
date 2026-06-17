/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.DirectionMappingSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import com.suntek.apiconnector.spec.model.MappingSpec;

/**
 * Resolves connector-level mapping vs per-direction endpoint {@code mappingOverride} (D-01).
 */
public final class MappingConfigResolver {

    private MappingConfigResolver() {
    }

    /**
     * Merges connector default mapping with endpoint override; override wins per direction when present.
     */
    public static ResolvedMapping resolve(ConnectorSpec spec, EndpointSpec endpoint) {
        MappingSpec connectorMapping = spec != null ? spec.mapping() : null;
        MappingSpec override = endpoint != null ? endpoint.mappingOverride() : null;
        return ResolvedMapping.of(
                resolveDirection(
                        connectorMapping != null ? connectorMapping.request() : null,
                        override != null ? override.request() : null),
                resolveDirection(
                        connectorMapping != null ? connectorMapping.response() : null,
                        override != null ? override.response() : null),
                resolveDirection(
                        connectorMapping != null ? connectorMapping.error() : null,
                        override != null ? override.error() : null));
    }

    /**
     * True when any direction has declarative rules or a Groovy script configured (D-23).
     */
    public static boolean hasAnyMapping(ResolvedMapping mapping) {
        return mapping != null
                && (mapping.hasRequest() || mapping.hasResponse() || mapping.hasError());
    }

    /**
     * Convenience wrapper over {@link #resolve(ConnectorSpec, EndpointSpec)}.
     */
    public static boolean hasAnyMapping(ConnectorSpec spec, EndpointSpec endpoint) {
        return hasAnyMapping(resolve(spec, endpoint));
    }

    private static ResolvedMapping.ResolvedDirection resolveDirection(
            DirectionMappingSpec connectorDirection,
            DirectionMappingSpec overrideDirection) {
        DirectionMappingSpec chosen = overrideDirection != null ? overrideDirection : connectorDirection;
        return ResolvedMapping.ResolvedDirection.from(chosen);
    }
}
