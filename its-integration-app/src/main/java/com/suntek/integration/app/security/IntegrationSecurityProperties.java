/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.app.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * API Key 鉴权配置。enabled=true 时必须配置至少一类密钥。
 */
@ConfigurationProperties(prefix = "integration.security")
public class IntegrationSecurityProperties {

    private boolean enabled;
    private String apiKeyHeader = "X-Integration-Api-Key";
    private List<String> runtimeApiKeys = new ArrayList<>();
    private List<String> adminApiKeys = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public List<String> getRuntimeApiKeys() {
        return runtimeApiKeys;
    }

    public void setRuntimeApiKeys(List<String> runtimeApiKeys) {
        this.runtimeApiKeys = runtimeApiKeys;
    }

    public List<String> getAdminApiKeys() {
        return adminApiKeys;
    }

    public void setAdminApiKeys(List<String> adminApiKeys) {
        this.adminApiKeys = adminApiKeys;
    }
}
