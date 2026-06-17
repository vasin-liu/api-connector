/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.cache;

/**
 * Cache key for OAuth tokens: connector + profile + scope.
 */
public record TokenCacheKey(String code3rd, String profileType, String scope) {
}
