/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.controller;

import com.suntek.integration.api.dto.ConnectorEndpointSummary;
import com.suntek.integration.api.dto.EndpointParamSummary;
import com.suntek.integration.api.dto.EndpointInvokeRequest;
import com.suntek.integration.api.dto.ProxyInvokeRequest;
import com.suntek.integration.api.dto.ProxyInvokeResponse;
import com.suntek.integration.api.invoke.InvokeHttpResponseMapper;
import com.suntek.integration.api.openapi.EndpointOpenApiMetadataResolver;
import com.suntek.integration.api.service.IntegrationInvokeService;
import com.suntek.integration.engine.ConnectorRegistry;
import com.suntek.integration.spec.model.ConnectorSpec;
import com.suntek.integration.spec.model.EndpointSpec;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.suntek.integration.api.invoke.ServletStreamingInvocationSink;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时 Invoke API（推荐）+ 兼容 proxy。
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Runtime · Invoke", description = "调用已发布第三方连接器。优先使用 endpointId；OpenAPI 中按厂家分组的操作见各 Connector 标签。")
public class IntegrationProxyController {

    private final IntegrationInvokeService invokeService;
    private final ConnectorRegistry registry;
    private final InvokeHttpResponseMapper responseMapper;

    public IntegrationProxyController(
            IntegrationInvokeService invokeService,
            ConnectorRegistry registry,
            InvokeHttpResponseMapper responseMapper) {
        this.invokeService = invokeService;
        this.registry = registry;
        this.responseMapper = responseMapper;
    }

    @Operation(
            summary = "列出连接器已登记端点",
            description = "返回 Spec endpoints[]，供 Swagger 与控制台试调选择。",
            operationId = "listConnectorEndpoints")
    @ApiResponse(responseCode = "200", description = "端点目录")
    @ApiResponse(responseCode = "404", description = "未知连接器",
            content = @Content(schema = @Schema(implementation = com.suntek.integration.api.dto.ApiErrorResponse.class)))
    @GetMapping("/{code3rd}/endpoints")
    public List<ConnectorEndpointSummary> listEndpoints(
            @Parameter(description = "连接器编码", example = "IDPS") @PathVariable String code3rd) {
        ConnectorSpec spec = registry.require(code3rd);
        List<ConnectorEndpointSummary> list = new ArrayList<>();
        if (spec.endpoints() == null) {
            return list;
        }
        for (EndpointSpec endpoint : spec.endpoints()) {
            if (endpoint.enabled() != null && !endpoint.enabled()) {
                continue;
            }
            list.add(ConnectorEndpointSummary.builder()
                    .id(endpoint.id())
                    .method(endpoint.method())
                    .path(endpoint.path())
                    .summary(EndpointOpenApiMetadataResolver.resolveSummary(endpoint))
                    .group(EndpointOpenApiMetadataResolver.resolveGroup(endpoint))
                    .enabled(true)
                    .invokeUrl("/api/v1/integrations/" + code3rd + "/endpoints/" + endpoint.id() + "/invoke")
                    .parameters(EndpointOpenApiMetadataResolver.resolveParameters(endpoint))
                    .build());
        }
        return list;
    }

    @Hidden
    @Operation(
            summary = "按 Spec 端点流式调用（SSE）",
            description = "透传厂家 text/event-stream 响应行。请求体与同步 invoke 相同。",
            operationId = "streamConnectorEndpoint")
    @PostMapping(
            value = "/{code3rd}/endpoints/{endpointId}/invoke/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamEndpoint(
            @Parameter(example = "BAIDU_WENXIN") @PathVariable String code3rd,
            @Parameter(example = "chatCompletionsPro") @PathVariable String endpointId,
            @RequestBody(required = false) EndpointInvokeRequest request) {
        return outputStream ->
                invokeService.streamEndpoint(
                        code3rd,
                        endpointId,
                        request,
                        new ServletStreamingInvocationSink(outputStream));
    }

    @Hidden
    @Operation(
            summary = "按 Spec 端点调用（推荐）",
            description = """
                    使用 Connector Spec 中登记的 endpointId，无需手写 path/method。
                    仅需传入 query、headers、body 等业务参数；认证由平台 Profile 自动完成。
                    """,
            operationId = "invokeConnectorEndpoint")
    @ApiResponse(responseCode = "200", description = "调用完成（平台 HTTP 通常为 200，厂家状态见 vendorHttpStatus）",
            content = @Content(schema = @Schema(implementation = ProxyInvokeResponse.class)))
    @PostMapping("/{code3rd}/endpoints/{endpointId}/invoke")
    public ResponseEntity<ProxyInvokeResponse> invokeEndpoint(
            @Parameter(example = "IDPS") @PathVariable String code3rd,
            @Parameter(example = "roadSpeeds") @PathVariable String endpointId,
            @RequestBody(required = false) EndpointInvokeRequest request) {
        return responseMapper.toResponse(invokeService.invokeEndpoint(code3rd, endpointId, request));
    }

    @Hidden
    @Operation(
            summary = "通用 invoke（endpointId 或 method+path）",
            description = """
                    推荐在 body 中仅传 endpointId + query。
                    高级场景可传 method + path（兼容旧字段 uri）。
                    """,
            operationId = "invokeConnector")
    @PostMapping("/{code3rd}/invoke")
    public ResponseEntity<ProxyInvokeResponse> invoke(
            @PathVariable String code3rd,
            @Valid @RequestBody ProxyInvokeRequest request) {
        return responseMapper.toResponse(invokeService.invoke(code3rd, request));
    }

    @Hidden
    @Operation(
            summary = "通用 proxy（兼容旧客户端）",
            description = "与 POST /invoke 等价，后续版本可能移除。请迁移至 /endpoints/{endpointId}/invoke。",
            operationId = "proxyConnector",
            deprecated = true)
    @PostMapping("/{code3rd}/proxy")
    public ResponseEntity<ProxyInvokeResponse> proxy(
            @PathVariable String code3rd,
            @Valid @RequestBody ProxyInvokeRequest request) {
        return invoke(code3rd, request);
    }
}
