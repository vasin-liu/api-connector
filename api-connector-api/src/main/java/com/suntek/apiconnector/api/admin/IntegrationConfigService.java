/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.connectors.BuiltinConnectorCatalogs;
import com.suntek.apiconnector.connectors.CatalogManagedSpecMerger;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.ConnectorSpecStatus;
import com.suntek.apiconnector.engine.store.ConnectorConfigStore;
import com.suntek.apiconnector.spec.ConnectorSpecParser;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 连接器配置保存、发布与校验（内存注册表，P2 换 DB）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Service
public class IntegrationConfigService {

    private final ConnectorRegistry registry;
    private final ProfileMetaCatalog profileMetaCatalog;
    private final ObjectMapper objectMapper;
    private final ConnectorConfigStore configStore;

    /**
     * 构造配置服务。
     *
     * @param registry           注册表
     * @param profileMetaCatalog Profile 元数据
     * @param objectMapper       JSON 工具
     * @param configStore        可选持久化（独立 JDBC）
     */
    public IntegrationConfigService(
            ConnectorRegistry registry,
            ProfileMetaCatalog profileMetaCatalog,
            ObjectMapper objectMapper,
            @Autowired(required = false) ConnectorConfigStore configStore) {
        this.registry = registry;
        this.profileMetaCatalog = profileMetaCatalog;
        this.objectMapper = objectMapper;
        this.configStore = configStore;
    }

    /**
     * 获取连接器完整配置。
     *
     * @param code3rd 连接器编码
     * @return 配置
     */
    public ConnectorConfigResponse getConfig(String code3rd) {
        ConnectorSpec spec = registry.require(code3rd);
        return toResponse(spec);
    }

    /**
     * 新建连接器。
     *
     * @param request 保存请求
     * @return 保存后的配置
     */
    public ConnectorConfigResponse create(SaveConnectorConfigRequest request) {
        ConnectorSpec spec = parseSpec(request.getSpec());
        CatalogManagedSpecMerger.assertCreatable(spec.code3rd());
        if (registry.find(spec.code3rd()).isPresent()) {
            throw new IllegalArgumentException("Connector already exists: " + spec.code3rd());
        }
        return applySave(spec, request);
    }

    /**
     * 更新已有连接器。
     *
     * @param code3rd 路径上的编码
     * @param request 保存请求
     * @return 保存后的配置
     */
    public ConnectorConfigResponse update(String code3rd, SaveConnectorConfigRequest request) {
        registry.require(code3rd);
        ConnectorSpec spec = parseSpec(request.getSpec());
        if (!code3rd.equals(spec.code3rd())) {
            throw new IllegalArgumentException("code3rd in body must match path: " + code3rd);
        }
        return applySave(spec, request);
    }

    /**
     * 删除连接器。
     *
     * @param code3rd 连接器编码
     */
    public void delete(String code3rd) {
        CatalogManagedSpecMerger.assertDeletable(code3rd);
        boolean removed = registry.remove(code3rd);
        if (configStore != null) {
            removed = configStore.delete(code3rd) || removed;
        }
        if (!removed) {
            throw new IllegalArgumentException("Unknown connector: " + code3rd);
        }
    }

    private ConnectorConfigResponse applySave(ConnectorSpec spec, SaveConnectorConfigRequest request) {
        spec = CatalogManagedSpecMerger.forAdminSave(spec);
        boolean publish = request.isPublish();
        validate(spec, publish);
        ConnectorSpecStatus status = publish ? ConnectorSpecStatus.PUBLISHED : ConnectorSpecStatus.DRAFT;
        registry.save(spec, request.getCredentials(), status);
        if (configStore != null) {
            configStore.save(spec, request.getCredentials(), status);
        }
        return toResponse(spec);
    }

    private ConnectorSpec parseSpec(Map<String, Object> specMap) {
        if (specMap == null || specMap.isEmpty()) {
            throw new IllegalArgumentException("spec is required");
        }
        ConnectorSpec spec = ConnectorSpecParser.parse(specMap);
        if (spec.code3rd() == null || spec.code3rd().isBlank()) {
            throw new IllegalArgumentException("spec.code3rd is required");
        }
        if (spec.baseUrl() == null || spec.baseUrl().isBlank()) {
            throw new IllegalArgumentException("spec.baseUrl is required");
        }
        return spec;
    }

    private void validate(ConnectorSpec spec, boolean publish) {
        String authType = ConnectorSpecJsonSupport.resolveAuthType(spec.auth());
        if (publish && !"none".equals(authType) && profileMetaCatalog.find(authType).isEmpty()
                && !authType.startsWith("pipeline:")) {
            throw new IllegalArgumentException("Unknown auth profile for publish: " + authType);
        }
        List<EndpointSpec> endpoints = spec.endpoints();
        if (endpoints != null) {
            Set<String> ids = new HashSet<>();
            for (EndpointSpec endpoint : endpoints) {
                if (endpoint.id() == null || endpoint.id().isBlank()) {
                    throw new IllegalArgumentException("endpoint.id is required");
                }
                if (!ids.add(endpoint.id())) {
                    throw new IllegalArgumentException("duplicate endpoint id: " + endpoint.id());
                }
            }
        }
    }

    private ConnectorConfigResponse toResponse(ConnectorSpec spec) {
        String code3rd = spec.code3rd();
        String status = registry.status(code3rd)
                .map(Enum::name)
                .orElse(ConnectorSpecStatus.PUBLISHED.name());
        return new ConnectorConfigResponse(
                code3rd,
                status,
                ConnectorSpecJsonSupport.resolveAuthType(spec.auth()),
                ConnectorSpecJsonSupport.toMap(spec),
                CredentialsView.masked(registry.credentials(code3rd)),
                BuiltinConnectorCatalogs.isManaged(code3rd));
    }
}
