/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.domain.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Immutable mapping pipeline input for declarative rules and Groovy scripts (D-11).
 */
public record MappingContext(
        String code3rd,
        MappingDirection direction,
        String rawBody,
        AuthContextSnapshot authSnapshot,
        EndpointMeta endpoint) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /**
     * Returns the raw request/response body string.
     */
    public String body() {
        return rawBody;
    }

    /**
     * Parses {@link #rawBody()} as a JSON object map for script bindings.
     */
    public Map<String, Object> bodyAsMap() {
        if (rawBody == null || rawBody.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawBody, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Mapping body is not valid JSON object: " + ex.getMessage(), ex);
        }
    }
}
