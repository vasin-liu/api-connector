/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 独立应用持久化配置（不依赖 system-manage）。
 */
@ConfigurationProperties(prefix = "integration.persistence")
public class IntegrationPersistenceProperties {

    /**
     * 配置源：classpath（YAML 示例）、memory（仅内存）、jdbc（内嵌/外接库）、composite（classpath + jdbc 已发布）。
     */
    private String source = "composite";

    /** 启动时从 jdbc 加载已发布配置到注册表。 */
    private boolean syncOnStartup = true;

    /** 定时从 jdbc 全量刷新；0 表示关闭。 */
    private long syncIntervalMs = 0;

    public String source() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean syncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }

    public long syncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public boolean useJdbc() {
        String s = source == null ? "" : source.toLowerCase();
        return "jdbc".equals(s) || "composite".equals(s);
    }
}
