/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期性从 JDBC 同步配置（仅当 {@code sync-interval-ms > 0} 时注册）。
 */
@Component
@ConditionalOnBean(ConnectorConfigSyncService.class)
@ConditionalOnExpression("${integration.persistence.sync-interval-ms:0} > 0")
public class ConnectorConfigPeriodicSyncJob {

    private final ConnectorConfigSyncService syncService;

    public ConnectorConfigPeriodicSyncJob(ConnectorConfigSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${integration.persistence.sync-interval-ms}")
    public void tick() {
        syncService.reloadFromStore();
    }
}
