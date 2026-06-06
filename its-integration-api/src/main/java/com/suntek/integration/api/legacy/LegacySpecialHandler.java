/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

/**
 * 无法通过简单路径别名完成的 legacy 请求（聚合、本地签名等）。
 */
public interface LegacySpecialHandler {

    Optional<ResponseEntity<String>> tryHandle(HttpServletRequest request, LegacyRouteResolver.ResolvedRoute route);
}
