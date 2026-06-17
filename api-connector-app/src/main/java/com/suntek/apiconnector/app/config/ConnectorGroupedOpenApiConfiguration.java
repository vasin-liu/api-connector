/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.api.openapi.ConnectorEndpointOpenApiExpander;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.Locale;

/**
 * 按厂家拆分的 Swagger 分组 + 端点级 OpenAPI 展开。
 */
@Configuration
public class ConnectorGroupedOpenApiConfiguration {

    @Bean
    @DependsOn("loadClasspathConnectors")
    public ConnectorEndpointOpenApiExpander connectorEndpointOpenApiExpander(
            ConnectorRegistry registry,
            ObjectMapper objectMapper) {
        return new ConnectorEndpointOpenApiExpander(registry, objectMapper);
    }

    @Bean
    @DependsOn("loadClasspathConnectors")
    public Object registerVendorGroupedOpenApi(ConnectorRegistry registry, ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof DefaultListableBeanFactory dlbf)) {
            return new Object();
        }
        for (ConnectorSpec spec : registry.listSpecs()) {
            String code = spec.code3rd();
            String beanName = "groupedOpenApiVendor_" + code;
            if (!dlbf.containsSingleton(beanName)) {
                dlbf.registerSingleton(beanName, GroupedOpenApi.builder()
                        .group("vendor-" + sanitize(code))
                        .displayName(code + " · 厂家 API")
                        .pathsToMatch("/api/v1/integrations/" + code + "/**")
                        .build());
            }
        }
        return new Object();
    }

    private static String sanitize(String code3rd) {
        return code3rd.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }
}
