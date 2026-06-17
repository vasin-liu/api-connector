/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 持久化模块配置（独立 JDBC，无 system-manage）。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IntegrationPersistenceProperties.class)
public class IntegrationPersistenceConfiguration {
}
