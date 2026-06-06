/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.engine;

import com.suntek.integration.spec.model.ConnectorSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时连接器注册表（后续可替换为 DB / 配置中心）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public class ConnectorRegistry {

    private final Map<String, ConnectorSpec> specs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> credentials = new ConcurrentHashMap<>();
    private final Map<String, ConnectorSpecStatus> statuses = new ConcurrentHashMap<>();

    /**
     * 注册连接器规格与凭证（已发布）。
     *
     * @param spec          连接器规格
     * @param credentialMap 凭证键值
     */
    public void register(ConnectorSpec spec, Map<String, String> credentialMap) {
        save(spec, credentialMap, ConnectorSpecStatus.PUBLISHED);
    }

    /**
     * 保存或更新连接器（热加载到运行时）。
     *
     * @param spec            连接器规格
     * @param credentialPatch 凭证补丁（{@code null} 值表示不修改该键）
     * @param status          发布状态
     */
    public void save(ConnectorSpec spec, Map<String, String> credentialPatch, ConnectorSpecStatus status) {
        String code3rd = spec.code3rd();
        specs.put(code3rd, spec);
        mergeCredentials(code3rd, credentialPatch);
        statuses.put(code3rd, status);
    }

    /**
     * 按 code3rd 获取规格，不存在则抛异常。
     *
     * @param code3rd 连接器编码
     * @return 规格
     */
    public ConnectorSpec require(String code3rd) {
        ConnectorSpec spec = specs.get(code3rd);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown connector: " + code3rd);
        }
        return spec;
    }

    /**
     * 按 code3rd 查找规格。
     *
     * @param code3rd 连接器编码
     * @return 规格
     */
    public Optional<ConnectorSpec> find(String code3rd) {
        return Optional.ofNullable(specs.get(code3rd));
    }

    /**
     * 获取凭证映射（内部使用，勿暴露给前端）。
     *
     * @param code3rd 连接器编码
     * @return 凭证
     */
    public Map<String, String> credentials(String code3rd) {
        return credentials.getOrDefault(code3rd, Map.of());
    }

    /**
     * 获取发布状态。
     *
     * @param code3rd 连接器编码
     * @return 状态，未注册则为 empty
     */
    public Optional<ConnectorSpecStatus> status(String code3rd) {
        return Optional.ofNullable(statuses.get(code3rd));
    }

    /**
     * 返回已注册连接器的只读快照。
     *
     * @return 连接器列表
     */
    public List<ConnectorSpec> listSpecs() {
        return Collections.unmodifiableList(new ArrayList<>(specs.values()));
    }

    /**
     * 删除连接器（管理端）。
     *
     * @param code3rd 连接器编码
     * @return 是否曾存在
     */
    public boolean remove(String code3rd) {
        specs.remove(code3rd);
        credentials.remove(code3rd);
        return statuses.remove(code3rd) != null;
    }

    private void mergeCredentials(String code3rd, Map<String, String> credentialPatch) {
        if (credentialPatch == null || credentialPatch.isEmpty()) {
            return;
        }
        Map<String, String> target = credentials.computeIfAbsent(code3rd, k -> new ConcurrentHashMap<>());
        credentialPatch.forEach((key, value) -> {
            if (value != null) {
                target.put(key, value);
            }
        });
    }
}
