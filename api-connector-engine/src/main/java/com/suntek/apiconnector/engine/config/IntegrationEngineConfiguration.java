/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.engine.config;

import com.suntek.apiconnector.auth.AuthEngine;
import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.auth.profile.AkskHmacSha256V1AuthProvider;
import com.suntek.apiconnector.auth.profile.ApiKeyQueryAuthProvider;
import com.suntek.apiconnector.auth.profile.BearerStaticAuthProvider;
import com.suntek.apiconnector.auth.profile.GaodeTrafficHmacAuthProvider;
import com.suntek.apiconnector.auth.profile.GroovyAuthScriptProvider;
import com.suntek.apiconnector.auth.profile.NoneAuthProvider;
import com.suntek.apiconnector.auth.profile.OAuth2ClientCredentialsAuthProvider;
import com.suntek.apiconnector.auth.profile.OAuth2TokenInQueryAuthProvider;
import com.suntek.apiconnector.auth.spi.AuthProvider;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.engine.ConnectorPublishListener;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.DefaultIntegrationOrchestrator;
import com.suntek.apiconnector.engine.ResolvedMappingCache;
import com.suntek.apiconnector.engine.ResponseEvaluator;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.JdkHttpTransport;
import com.suntek.apiconnector.mapping.DeclarativeRuleExecutor;
import com.suntek.apiconnector.mapping.GroovyMappingScriptProvider;
import com.suntek.apiconnector.mapping.MappingEngineImpl;
import com.suntek.apiconnector.mapping.TransformPipeline;
import com.suntek.apiconnector.mapping.TransformStepRegistry;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.mapping.spi.TransformStep;
import com.suntek.apiconnector.mapping.transform.Sm4DecryptTransformStep;
import com.suntek.apiconnector.mapping.transform.Sm4EncryptTransformStep;
import com.suntek.apiconnector.mapping.transform.StubTransformStep;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import org.springframework.beans.factory.annotation.Value;
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
    public ConnectorPublishListener connectorPublishListener(
            ScriptCompileService scriptCompileService,
            TokenCache tokenCache,
            TransformStepRegistry transformStepRegistry,
            ResolvedMappingCache resolvedMappingCache) {
        return new ConnectorPublishListener(
                scriptCompileService, tokenCache, transformStepRegistry, resolvedMappingCache);
    }

    /**
     * Resolved-mapping cache Bean (D-21).
     *
     * @return resolved-mapping cache invalidated on connector publish
     */
    @Bean
    public ResolvedMappingCache resolvedMappingCache() {
        return new ResolvedMappingCache();
    }

    @Bean
    public ConnectorRegistry connectorRegistry(ConnectorPublishListener connectorPublishListener) {
        return new ConnectorRegistry(connectorPublishListener);
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
            HttpTransport httpTransport,
            MappingEngine mappingEngine,
            TransformPipeline transformPipeline,
            ResolvedMappingCache resolvedMappingCache,
            @Value("${integration.invoke.mapping-enabled:true}") boolean mappingEnabled) {
        return new DefaultIntegrationOrchestrator(
                registry,
                authEngine,
                httpTransport,
                new ResponseEvaluator(),
                mappingEngine,
                transformPipeline,
                resolvedMappingCache,
                mappingEnabled);
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
    public TokenCache tokenCache() {
        return new TokenCache();
    }

    @Bean
    public OAuth2ClientCredentialsAuthProvider oauth2ClientCredentialsAuthProvider(TokenCache tokenCache) {
        return new OAuth2ClientCredentialsAuthProvider(tokenCache);
    }

    @Bean
    public OAuth2TokenInQueryAuthProvider oauth2TokenInQueryAuthProvider(TokenCache tokenCache) {
        return new OAuth2TokenInQueryAuthProvider(tokenCache);
    }

    @Bean
    public GaodeTrafficHmacAuthProvider gaodeTrafficHmacAuthProvider() {
        return new GaodeTrafficHmacAuthProvider();
    }

    @Bean
    public ScriptCompileService scriptCompileService() {
        return new ScriptCompileService();
    }

    @Bean
    public GroovyAuthScriptProvider groovyAuthScriptProvider(ScriptCompileService scriptCompileService) {
        return new GroovyAuthScriptProvider(scriptCompileService);
    }

    @Bean
    public DeclarativeRuleExecutor declarativeRuleExecutor() {
        return new DeclarativeRuleExecutor();
    }

    @Bean
    public GroovyMappingScriptProvider groovyMappingScriptProvider(ScriptCompileService scriptCompileService) {
        return new GroovyMappingScriptProvider(scriptCompileService);
    }

    @Bean
    public MappingEngine mappingEngine(
            DeclarativeRuleExecutor declarativeRuleExecutor,
            GroovyMappingScriptProvider groovyMappingScriptProvider) {
        return new MappingEngineImpl(declarativeRuleExecutor, groovyMappingScriptProvider);
    }

    @Bean
    public Sm4EncryptTransformStep sm4EncryptTransformStep() {
        return new Sm4EncryptTransformStep();
    }

    @Bean
    public Sm4DecryptTransformStep sm4DecryptTransformStep() {
        return new Sm4DecryptTransformStep();
    }

    @Bean
    public StubTransformStep businessEnvelopeTransformStep() {
        return StubTransformStep.businessEnvelope();
    }

    @Bean
    public TransformStepRegistry transformStepRegistry(List<TransformStep> transformSteps) {
        return new TransformStepRegistry(transformSteps);
    }

    @Bean
    public TransformPipeline transformPipeline(TransformStepRegistry transformStepRegistry) {
        return new TransformPipeline(transformStepRegistry);
    }

    // Wave 1 L2 profiles (oauth2_password, bearer_from_login, sm3_header_sign_v1) are inventory-gated
    // per docs/legacy-auth-inventory.md — not required for Phase 1 Wave 1 catalogs (D-01, D-04).
}
