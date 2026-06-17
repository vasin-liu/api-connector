/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

/**
 * Connector-level mapping for request, response, and error directions.
 */
public record MappingSpec(
        DirectionMappingSpec request,
        DirectionMappingSpec response,
        DirectionMappingSpec error) {
}
