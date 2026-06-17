/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.domain.model;

/**
 * Endpoint identity subset exposed to Groovy mapping bindings (D-11).
 */
public record EndpointMeta(String id, String method, String path) {
}
