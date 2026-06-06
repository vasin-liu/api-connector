/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.persistence.web;

import com.suntek.integration.persistence.ConnectorConfigSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端手动触发：持久化层 → 运行时注册表 全量刷新。
 */
@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnBean(ConnectorConfigSyncService.class)
public class IntegrationSyncController {

    private final ConnectorConfigSyncService syncService;

    public IntegrationSyncController(ConnectorConfigSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 从本地库重新加载已发布配置到内存注册表。
     *
     * @return 加载条数
     */
    @PostMapping("/sync")
    public Map<String, Object> reloadFromStore() {
        int count = syncService.reloadFromStore();
        return Map.of("synced", count);
    }
}
