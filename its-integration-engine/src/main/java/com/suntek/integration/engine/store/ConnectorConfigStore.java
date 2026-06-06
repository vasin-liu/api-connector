/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine.store;

import com.suntek.integration.engine.ConnectorSpecStatus;
import com.suntek.integration.spec.model.ConnectorSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 连接器配置持久化端口（独立应用内嵌库 / 外接 DB 实现）。
 */
public interface ConnectorConfigStore {

    /**
     * 保存或更新连接器规格与凭证。
     *
     * @param spec            规格
     * @param credentials     凭证（明文，由实现层负责落库）
     * @param status          发布状态
     */
    void save(ConnectorSpec spec, Map<String, String> credentials, ConnectorSpecStatus status);

    /**
     * 删除连接器。
     *
     * @param code3rd 连接器编码
     * @return 是否曾存在
     */
    boolean delete(String code3rd);

    /**
     * 列出所有已发布配置。
     *
     * @return 已发布记录
     */
    List<StoredConnectorConfig> listPublished();

    /**
     * 按编码查找（任意状态）。
     *
     * @param code3rd 连接器编码
     * @return 配置
     */
    Optional<StoredConnectorConfig> find(String code3rd);
}
