/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Invoke 审计与限流配置。
 */
@ConfigurationProperties(prefix = "integration.invoke")
public class IntegrationInvokeProperties {

    /**
     * 平台 HTTP 状态：{@code platform_ok} 恒 200（厂家状态在 body.vendorHttpStatus）；
     * {@code vendor} 透传厂家 HTTP 状态（兼容旧客户端）。
     */
    private PlatformHttpStatusMode platformHttpStatus = PlatformHttpStatusMode.PLATFORM_OK;

    private boolean auditEnabled = true;
    private int rateLimitPerCode3rdPerMinute;

    /**
     * 全局映射开关（{@code integration.invoke.mapping-enabled}，默认 {@code true}，D-22）。
     * 关闭时整条管线走透传，作为集成/灰度的一键回退；引擎独立读取原始属性，
     * 此为 api 层的类型化访问器（供管理台/控制台使用）。
     */
    private boolean mappingEnabled = true;

    public enum PlatformHttpStatusMode {
        PLATFORM_OK,
        VENDOR
    }

    public PlatformHttpStatusMode getPlatformHttpStatus() {
        return platformHttpStatus;
    }

    public void setPlatformHttpStatus(PlatformHttpStatusMode platformHttpStatus) {
        this.platformHttpStatus = platformHttpStatus;
    }

    /**
     * 计算对外 HTTP 状态码。
     */
    public int resolveHttpStatus(int vendorHttpStatus, boolean businessSuccess) {
        if (platformHttpStatus == PlatformHttpStatusMode.VENDOR) {
            return vendorHttpStatus > 0 ? vendorHttpStatus : 200;
        }
        return 200;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public int getRateLimitPerCode3rdPerMinute() {
        return rateLimitPerCode3rdPerMinute;
    }

    public void setRateLimitPerCode3rdPerMinute(int rateLimitPerCode3rdPerMinute) {
        this.rateLimitPerCode3rdPerMinute = rateLimitPerCode3rdPerMinute;
    }

    /**
     * 是否启用数据映射管线（默认 {@code true}）。
     *
     * @return {@code true} 表示启用映射；{@code false} 表示整条管线透传
     */
    public boolean isMappingEnabled() {
        return mappingEnabled;
    }

    /**
     * 设置数据映射管线总开关。
     *
     * @param mappingEnabled {@code true} 启用映射，{@code false} 关闭（全透传）
     */
    public void setMappingEnabled(boolean mappingEnabled) {
        this.mappingEnabled = mappingEnabled;
    }
}
