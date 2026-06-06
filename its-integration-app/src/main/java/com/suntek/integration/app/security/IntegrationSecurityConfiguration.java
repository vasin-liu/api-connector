/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.app.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.suntek.integration.api.legacy.IntegrationLegacyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 可选 API Key 鉴权（integration.security.enabled）。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({IntegrationSecurityProperties.class, IntegrationLegacyProperties.class})
public class IntegrationSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain integrationSecurityFilterChain(
            HttpSecurity http,
            IntegrationSecurityProperties properties,
            IntegrationLegacyProperties legacyProperties) throws Exception {
        String[] matchers = buildProtectedMatchers(legacyProperties);
        http.securityMatcher(matchers)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        if (properties.isEnabled()) {
            http.addFilterBefore(
                    new IntegrationApiKeyFilter(properties), UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }

    private static String[] buildProtectedMatchers(IntegrationLegacyProperties legacyProperties) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add("/api/v1/integrations/**");
        paths.add("/api/v1/admin/**");
        paths.add("/console/**");
        if (legacyProperties.isEnabled()) {
            for (IntegrationLegacyProperties.RouteMapping route : legacyProperties.getRoutes()) {
                if (route.getPathPrefix() != null && !route.getPathPrefix().isBlank()) {
                    paths.add(route.getPathPrefix() + "/**");
                }
            }
        }
        return paths.toArray(String[]::new);
    }

    @Bean
    @Order(2)
    SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
