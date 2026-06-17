/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.auth;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.context.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证引擎：支持单 Profile 与 Pipeline 顺序执行。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public class AuthEngine {

    private final Map<String, AuthProvider> providersByType;

    /**
     * 构造认证引擎。
     *
     * @param providers 已注册的认证实现
     */
    public AuthEngine(List<AuthProvider> providers) {
        this.providersByType = new HashMap<>();
        for (AuthProvider p : providers) {
            providersByType.put(p.profileType(), p);
        }
    }

    /**
     * 按 auth 配置执行认证（支持 {@code pipeline} 列表）。
     *
     * @param context 认证上下文
     * @return 合并后的认证结果
     */
    @SuppressWarnings("unchecked")
    public AuthOutcome authenticate(AuthContext context) {
        Map<String, Object> auth = context.authConfig();
        if (auth == null || auth.isEmpty()) {
            return AuthOutcome.empty();
        }
        Object pipeline = auth.get("pipeline");
        if (pipeline instanceof List) {
            List<?> steps = (List<?>) pipeline;
            AuthOutcome merged = AuthOutcome.empty();
            for (Object step : steps) {
                if (step instanceof Map) {
                    merged = merge(merged, runStep(context, (Map<String, Object>) step));
                }
            }
            return merged;
        }
        return runStep(context, auth);
    }

    private AuthOutcome runStep(AuthContext context, Map<String, Object> stepConfig) {
        String type = String.valueOf(stepConfig.get("type"));
        AuthProvider provider = providersByType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No AuthProvider for type: " + type);
        }
        AuthContext stepCtx = new AuthContext(
                context.code3rd(),
                context.baseUrl(),
                context.method(),
                context.path(),
                context.query(),
                context.requestBody(),
                stepConfig,
                context.credentials(),
                context.ext());
        return provider.apply(stepCtx);
    }

    private static AuthOutcome merge(AuthOutcome a, AuthOutcome b) {
        Map<String, String> headers = new HashMap<>(a.headers());
        headers.putAll(b.headers());
        Map<String, String> query = new HashMap<>(a.query());
        query.putAll(b.query());
        String body = b.mutatedBody() != null ? b.mutatedBody() : a.mutatedBody();
        return new AuthOutcome(headers, query, body);
    }
}
