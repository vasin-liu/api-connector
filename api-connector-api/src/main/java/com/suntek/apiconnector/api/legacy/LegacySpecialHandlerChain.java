/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LegacySpecialHandlerChain {

    private final List<LegacySpecialHandler> handlers;

    public LegacySpecialHandlerChain(List<LegacySpecialHandler> handlers) {
        this.handlers = handlers;
    }

    public Optional<org.springframework.http.ResponseEntity<String>> tryHandle(
            HttpServletRequest request, LegacyRouteResolver.ResolvedRoute route) {
        for (LegacySpecialHandler handler : handlers) {
            Optional<org.springframework.http.ResponseEntity<String>> result = handler.tryHandle(request, route);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
