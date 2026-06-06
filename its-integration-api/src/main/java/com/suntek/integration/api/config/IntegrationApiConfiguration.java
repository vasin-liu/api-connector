/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.config;

import com.suntek.integration.api.invoke.InvokeRateLimiter;
import com.suntek.integration.api.legacy.IntegrationLegacyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 层 Bean 配置。
 */
@Configuration
@EnableConfigurationProperties({IntegrationInvokeProperties.class, IntegrationLegacyProperties.class})
public class IntegrationApiConfiguration {

    @Bean
    InvokeRateLimiter invokeRateLimiter(IntegrationInvokeProperties properties) {
        InvokeRateLimiter limiter = new InvokeRateLimiter();
        limiter.configure(properties.getRateLimitPerCode3rdPerMinute());
        return limiter;
    }
}
