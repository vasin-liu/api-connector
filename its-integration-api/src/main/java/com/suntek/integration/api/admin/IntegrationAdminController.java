/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.integration.connectors.BuiltinConnectorCatalogs;
import com.suntek.integration.api.dto.EndpointInvokeRequest;
import com.suntek.integration.api.dto.ProxyInvokeRequest;
import com.suntek.integration.api.dto.ProxyInvokeResponse;
import com.suntek.integration.api.invoke.InvokeContext;
import com.suntek.integration.api.invoke.InvokeHttpResponseMapper;
import com.suntek.integration.api.service.IntegrationInvokeService;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.engine.ConnectorSpecStatus;
import com.suntek.integration.spec.model.ConnectorSpec;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理控制台 BFF — 与 {@code its-integration-ui} 同端口访问。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin · Connectors", description = "连接器 Spec 与凭证的配置、发布（控制台 BFF）")
public class IntegrationAdminController {

    private final ConnectorRegistry registry;
    private final ProfileMetaCatalog profileMetaCatalog;
    private final IntegrationConfigService configService;
    private final IntegrationInvokeService invokeService;
    private final InvokeHttpResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public IntegrationAdminController(
            ConnectorRegistry registry,
            ProfileMetaCatalog profileMetaCatalog,
            IntegrationConfigService configService,
            IntegrationInvokeService invokeService,
            InvokeHttpResponseMapper responseMapper,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.profileMetaCatalog = profileMetaCatalog;
        this.configService = configService;
        this.invokeService = invokeService;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出运行时已注册的连接器（不含凭证明文）。
     *
     * @return 连接器摘要列表
     */
    @GetMapping("/connectors")
    public List<ConnectorSummaryResponse> listConnectors() {
        return registry.listSpecs().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 获取连接器完整配置（凭证脱敏）。
     *
     * @param code3rd 连接器编码
     * @return 配置
     */
    @GetMapping("/connectors/{code3rd}")
    public ConnectorConfigResponse getConnector(@PathVariable String code3rd) {
        return configService.getConfig(code3rd);
    }

    /**
     * 新建连接器。
     *
     * @param request 配置请求
     * @return 201 + 配置
     */
    @PostMapping("/connectors")
    public ResponseEntity<ConnectorConfigResponse> createConnector(@RequestBody SaveConnectorConfigRequest request) {
        ConnectorConfigResponse body = configService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * 保存草稿或发布更新。
     *
     * @param code3rd 连接器编码
     * @param request 配置请求
     * @return 更新后的配置
     */
    @PutMapping("/connectors/{code3rd}")
    public ConnectorConfigResponse saveConnector(
            @PathVariable String code3rd,
            @RequestBody SaveConnectorConfigRequest request) {
        return configService.update(code3rd, request);
    }

    /**
     * 发布连接器（等价于 PUT 且 publish=true）。
     *
     * @param code3rd 连接器编码
     * @param request 配置请求
     * @return 发布后的配置
     */
    @PostMapping("/connectors/{code3rd}/publish")
    public ConnectorConfigResponse publishConnector(
            @PathVariable String code3rd,
            @RequestBody SaveConnectorConfigRequest request) {
        request.setPublish(true);
        return configService.update(code3rd, request);
    }

    /**
     * 删除连接器。
     *
     * @param code3rd 连接器编码
     */
    @DeleteMapping("/connectors/{code3rd}")
    public ResponseEntity<Void> deleteConnector(@PathVariable String code3rd) {
        configService.delete(code3rd);
        return ResponseEntity.noContent().build();
    }

    /**
     * 列出全部 Auth Profile UI 元数据。
     *
     * @return Profile 元数据
     */
    @GetMapping("/profiles")
    @Tag(name = "Admin · Profiles")
    public List<Map<String, Object>> listProfiles() {
        return profileMetaCatalog.listAll();
    }

    /**
     * 按 ID 获取 Profile 元数据。
     *
     * @param profileId Profile 标识
     * @return 元数据或 404
     */
    @GetMapping("/profiles/{profileId}")
    @Tag(name = "Admin · Profiles")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable String profileId) {
        return profileMetaCatalog.find(profileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 控制台试调：允许 endpointId 或 method+path（不受 strictEndpoints 限制）。
     */
    @PostMapping("/connectors/{code3rd}/trial/invoke")
    @Tag(name = "Admin · Trial")
    public ResponseEntity<ProxyInvokeResponse> trialInvoke(
            @PathVariable String code3rd,
            @Valid @RequestBody ProxyInvokeRequest request) {
        if (request.getEndpointId() != null && !request.getEndpointId().isBlank()
                && (request.getMethod() == null || request.getMethod().isBlank())) {
            EndpointInvokeRequest ep = new EndpointInvokeRequest();
            ep.setQuery(request.getQuery());
            ep.setHeaders(request.getHeaders());
            ep.setBody(request.getBody());
            return responseMapper.toResponse(
                    invokeService.invokeEndpoint(code3rd, request.getEndpointId(), ep, InvokeContext.ADMIN_TRIAL));
        }
        return responseMapper.toResponse(invokeService.invoke(code3rd, request, InvokeContext.ADMIN_TRIAL));
    }

    private ConnectorSummaryResponse toSummary(ConnectorSpec spec) {
        Map<String, Object> specMap = ConnectorSpecJsonSupport.toMap(spec);
        int endpointCount = spec.endpoints() == null ? 0 : spec.endpoints().size();
        String authType = ConnectorSpecJsonSupport.resolveAuthType(spec.auth());
        String specStatus = registry.status(spec.code3rd())
                .map(ConnectorSpecStatus::name)
                .orElse(ConnectorSpecStatus.PUBLISHED.name());
        return new ConnectorSummaryResponse(
                spec.code3rd(),
                spec.version(),
                spec.baseUrl(),
                spec.protocol(),
                authType,
                endpointCount,
                specMap,
                specStatus,
                BuiltinConnectorCatalogs.isManaged(spec.code3rd()));
    }
}
