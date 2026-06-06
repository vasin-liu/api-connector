/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.auth.spi;

import com.suntek.integration.auth.context.AuthContext;
import com.suntek.integration.auth.context.AuthOutcome;

/**
 * 认证策略 SPI：将 Profile 配置应用于即将发出的 HTTP 请求。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public interface AuthProvider {

    /**
     * 本实现支持的 Profile 类型标识（如 {@code oauth2_client_credentials}）。
     *
     * @return 类型 ID
     */
    String profileType();

    /**
     * 应用认证：写 Header、Query、签名或刷新 Token。
     *
     * @param context 认证上下文
     * @return 认证结果（含待合并的请求头/查询参数）
     */
    AuthOutcome apply(AuthContext context);
}
