/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 凭证视图（返回给 UI，已脱敏）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialsView {

    private String appId;
    private String appSecret;
    private String publicKey;

    /**
     * 从运行时凭证构建脱敏视图。
     *
     * @param credentials 原始凭证
     * @return 脱敏视图
     */
    public static CredentialsView masked(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return new CredentialsView(null, null, null);
        }
        return new CredentialsView(
                credentials.get("appId"),
                maskSecret(credentials.get("appSecret")),
                maskSecret(credentials.get("publicKey")));
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "******";
    }
}
