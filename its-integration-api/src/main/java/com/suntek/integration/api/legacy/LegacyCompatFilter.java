/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.legacy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 统一处理 {@link IntegrationLegacyProperties} 中登记的旧 URL 前缀（GET/POST 透传）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "integration.legacy", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LegacyCompatFilter extends OncePerRequestFilter {

    private static final String IDPS_GET_EXCHANGE = "/idps/getExchange";

    private final IntegrationLegacyProperties legacyProperties;
    private final LegacyRouteResolver routeResolver;
    private final ThirdpartLegacyDispatcher dispatcher;
    private final LegacyForwardResolver forwardResolver;
    private final LegacySpecialHandlerChain specialHandlerChain;
    private final ObjectMapper objectMapper;

    public LegacyCompatFilter(
            IntegrationLegacyProperties legacyProperties,
            LegacyRouteResolver routeResolver,
            ThirdpartLegacyDispatcher dispatcher,
            LegacyForwardResolver forwardResolver,
            LegacySpecialHandlerChain specialHandlerChain,
            ObjectMapper objectMapper) {
        this.legacyProperties = legacyProperties;
        this.routeResolver = routeResolver;
        this.dispatcher = dispatcher;
        this.forwardResolver = forwardResolver;
        this.specialHandlerChain = specialHandlerChain;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!legacyProperties.isEnabled()) {
            return true;
        }
        return routeResolver.resolve(request.getRequestURI()).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var resolved = routeResolver.resolve(request.getRequestURI()).orElseThrow();
        Optional<ResponseEntity<String>> special = specialHandlerChain.tryHandle(request, resolved);
        if (special.isPresent()) {
            writeResponse(response, special.get());
            return;
        }
        String method = request.getMethod();
        if (HttpMethod.GET.name().equalsIgnoreCase(method)) {
            handleGet(request, response, resolved);
            return;
        }
        if (HttpMethod.POST.name().equalsIgnoreCase(method)) {
            handlePost(request, response, resolved);
            return;
        }
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    private void handleGet(
            HttpServletRequest request, HttpServletResponse response, LegacyRouteResolver.ResolvedRoute route)
            throws IOException {
        LegacyForwardPlan plan = forwardResolver.resolve(
                route, HttpMethod.GET, ThirdpartLegacyDispatcher.queryParams(request), null);
        writeResponse(response, dispatcher.forward(plan));
    }

    private void handlePost(
            HttpServletRequest request, HttpServletResponse response, LegacyRouteResolver.ResolvedRoute route)
            throws IOException {
        if (IDPS_GET_EXCHANGE.equals(request.getRequestURI())) {
            LegacyGetExchangeRequest exchange = readJson(request, LegacyGetExchangeRequest.class);
            ResponseEntity<String> entity = dispatcher.forwardExchange(route.code3rd(), exchange);
            writeResponse(response, entity);
            return;
        }
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        LegacyForwardPlan plan = forwardResolver.resolve(
                route, HttpMethod.POST, ThirdpartLegacyDispatcher.queryParams(request), body);
        writeResponse(response, dispatcher.forward(plan));
    }

    private void writeResponse(HttpServletResponse response, ResponseEntity<String> entity) throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                response.setHeader(name, values.getFirst());
            }
        });
        if (entity.getBody() != null) {
            response.getOutputStream().write(entity.getBody().getBytes(StandardCharsets.UTF_8));
        }
    }

    private <T> T readJson(HttpServletRequest request, Class<T> type) throws IOException {
        try {
            return objectMapper.readValue(request.getInputStream(), type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body", e);
        }
    }
}
