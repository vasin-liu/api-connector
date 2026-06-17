/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 按路径校验 API Key：runtime 与 admin 使用不同密钥列表。
 */
public class IntegrationApiKeyFilter extends OncePerRequestFilter {

    private final IntegrationSecurityProperties properties;
    private final Set<String> runtimeKeys;
    private final Set<String> adminKeys;

    public IntegrationApiKeyFilter(IntegrationSecurityProperties properties) {
        this.properties = properties;
        this.runtimeKeys = Set.copyOf(properties.getRuntimeApiKeys());
        this.adminKeys = Set.copyOf(properties.getAdminApiKeys());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return !(path.startsWith("/api/v1/integrations")
                || path.startsWith("/api/v1/admin")
                || path.startsWith("/console"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean adminPath = path.startsWith("/api/v1/admin") || path.startsWith("/console");
        Set<String> allowed = adminPath ? adminKeys : runtimeKeys;
        if (allowed.isEmpty()) {
            unauthorized(response, adminPath ? "ADMIN_API_KEYS_NOT_CONFIGURED" : "RUNTIME_API_KEYS_NOT_CONFIGURED");
            return;
        }
        String provided = request.getHeader(properties.getApiKeyHeader());
        if (provided == null || provided.isBlank() || !allowed.contains(provided)) {
            unauthorized(response, "INVALID_API_KEY");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":\"" + code + "\",\"message\":\"Authentication required\"}";
        response.getWriter().write(body);
    }
}
