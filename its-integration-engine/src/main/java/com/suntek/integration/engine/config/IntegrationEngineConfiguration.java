/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.engine.config;

import com.suntek.integration.auth.AuthEngine;
import com.suntek.integration.auth.profile.AkskHmacSha256V1AuthProvider;
import com.suntek.integration.auth.profile.ApiKeyQueryAuthProvider;
import com.suntek.integration.auth.profile.BearerStaticAuthProvider;
import com.suntek.integration.auth.profile.GaodeTrafficHmacAuthProvider;
import com.suntek.integration.auth.profile.NoneAuthProvider;
import com.suntek.integration.auth.profile.OAuth2ClientCredentialsAuthProvider;
import com.suntek.integration.auth.profile.OAuth2TokenInQueryAuthProvider;
import com.suntek.integration.auth.spi.AuthProvider;
import com.suntek.integration.domain.spi.IntegrationOrchestrator;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.engine.DefaultIntegrationOrchestrator;
import com.suntek.integration.engine.ResponseEvaluator;
import com.suntek.integration.engine.transport.HttpTransport;
import com.suntek.integration.engine.transport.JdkHttpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 引擎层 Spring 配置。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Configuration
public class IntegrationEngineConfiguration {

    /**
     * 连接器注册表 Bean。
     *
     * @return 注册表
     */
    @Bean
    public ConnectorRegistry connectorRegistry() {
        return new ConnectorRegistry();
    }

    /**
     * 认证引擎 Bean。
     *
     * @param providers 所有 {@link AuthProvider} 实现
     * @return 认证引擎
     */
    @Bean
    public AuthEngine authEngine(List<AuthProvider> providers) {
        return new AuthEngine(providers);
    }

    /**
     * HTTP 传输 Bean。
     *
     * @return JDK HttpClient 实现
     */
    @Bean
    public HttpTransport httpTransport() {
        return new JdkHttpTransport();
    }

    /**
     * 编排器 Bean。
     *
     * @param registry          注册表
     * @param authEngine        认证引擎
     * @param httpTransport     HTTP 传输
     * @return 默认编排器
     */
    @Bean
    public IntegrationOrchestrator integrationOrchestrator(
            ConnectorRegistry registry,
            AuthEngine authEngine,
            HttpTransport httpTransport) {
        return new DefaultIntegrationOrchestrator(
                registry, authEngine, httpTransport, new ResponseEvaluator());
    }

    /**
     * 内置无认证 Profile。
     *
     * @return NoneAuthProvider
     */
    @Bean
    public NoneAuthProvider noneAuthProvider() {
        return new NoneAuthProvider();
    }

    /**
     * IDPS / Traffic AK/SK HMAC Profile.
     *
     * @return AkskHmacSha256V1AuthProvider
     */
    @Bean
    public AkskHmacSha256V1AuthProvider akskHmacSha256V1AuthProvider() {
        return new AkskHmacSha256V1AuthProvider();
    }

    @Bean
    public BearerStaticAuthProvider bearerStaticAuthProvider() {
        return new BearerStaticAuthProvider();
    }

    @Bean
    public ApiKeyQueryAuthProvider apiKeyQueryAuthProvider() {
        return new ApiKeyQueryAuthProvider();
    }

    @Bean
    public OAuth2ClientCredentialsAuthProvider oauth2ClientCredentialsAuthProvider() {
        return new OAuth2ClientCredentialsAuthProvider();
    }

    @Bean
    public OAuth2TokenInQueryAuthProvider oauth2TokenInQueryAuthProvider() {
        return new OAuth2TokenInQueryAuthProvider();
    }

    @Bean
    public GaodeTrafficHmacAuthProvider gaodeTrafficHmacAuthProvider() {
        return new GaodeTrafficHmacAuthProvider();
    }
}
