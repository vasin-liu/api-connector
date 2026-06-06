/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine.store;

import com.suntek.integration.engine.ConnectorSpecStatus;
import com.suntek.integration.spec.model.ConnectorSpec;

import java.util.Map;

/**
 * 已持久化的连接器快照。
 */
public final class StoredConnectorConfig {

    private final ConnectorSpec spec;
    private final Map<String, String> credentials;
    private final ConnectorSpecStatus status;

    public StoredConnectorConfig(
            ConnectorSpec spec,
            Map<String, String> credentials,
            ConnectorSpecStatus status) {
        this.spec = spec;
        this.credentials = credentials;
        this.status = status;
    }

    public ConnectorSpec spec() {
        return spec;
    }

    public Map<String, String> credentials() {
        return credentials;
    }

    public ConnectorSpecStatus status() {
        return status;
    }
}
