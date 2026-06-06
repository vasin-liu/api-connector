/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.persistence;

import com.suntek.integration.connectors.CatalogManagedSpecMerger;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.engine.ConnectorSpecStatus;
import com.suntek.integration.engine.store.ConnectorConfigStore;
import com.suntek.integration.engine.store.StoredConnectorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从本地 JDBC 存储同步已发布配置到 {@link ConnectorRegistry}。
 */
@Service
@ConditionalOnBean(ConnectorConfigStore.class)
public class ConnectorConfigSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorConfigSyncService.class);

    private final ConnectorRegistry registry;
    private final ConnectorConfigStore store;
    private final IntegrationPersistenceProperties properties;

    public ConnectorConfigSyncService(
            ConnectorRegistry registry,
            ConnectorConfigStore store,
            IntegrationPersistenceProperties properties) {
        this.registry = registry;
        this.store = store;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.syncOnStartup() && properties.useJdbc()) {
            reloadFromStore();
        }
    }

    /**
     * 从持久化层全量加载已发布连接器到运行时注册表。
     *
     * @return 加载条数
     */
    public int reloadFromStore() {
        List<StoredConnectorConfig> published = store.listPublished();
        int count = 0;
        for (StoredConnectorConfig item : published) {
            if (item == null || item.spec() == null) {
                continue;
            }
            registry.save(
                    CatalogManagedSpecMerger.forRuntime(item.spec()),
                    item.credentials(),
                    ConnectorSpecStatus.PUBLISHED);
            count++;
        }
        LOG.info("Reloaded {} published connector(s) from local store", count);
        return count;
    }
}
