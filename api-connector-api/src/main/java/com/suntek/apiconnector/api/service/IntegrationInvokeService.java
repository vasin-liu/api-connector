/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.service;

import com.suntek.apiconnector.api.config.IntegrationInvokeProperties;
import com.suntek.apiconnector.api.dto.EndpointInvokeRequest;
import com.suntek.apiconnector.api.dto.ProxyInvokeRequest;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import com.suntek.apiconnector.api.invoke.InvokeAuditEvent;
import com.suntek.apiconnector.api.invoke.InvokeAuditLogger;
import com.suntek.apiconnector.api.invoke.InvokeContext;
import com.suntek.apiconnector.api.invoke.InvokeRateLimiter;
import com.suntek.apiconnector.api.invoke.StrictEndpointsEnforcer;
import com.suntek.apiconnector.domain.model.ConnectorCode;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.EndpointResolver;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 运行时 Invoke 编排（endpointId 解析 + Orchestrator）。
 */
@Service
public class IntegrationInvokeService {

    private final ConnectorRegistry registry;
    private final IntegrationOrchestrator orchestrator;
    private final InvokeRateLimiter rateLimiter;
    private final InvokeAuditLogger auditLogger;
    private final IntegrationInvokeProperties invokeProperties;

    public IntegrationInvokeService(
            ConnectorRegistry registry,
            IntegrationOrchestrator orchestrator,
            InvokeRateLimiter rateLimiter,
            InvokeAuditLogger auditLogger,
            IntegrationInvokeProperties invokeProperties) {
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.invokeProperties = invokeProperties;
    }

    public ProxyInvokeResponse invoke(String code3rd, ProxyInvokeRequest request) {
        return invoke(code3rd, request, InvokeContext.RUNTIME);
    }

    public ProxyInvokeResponse invoke(String code3rd, ProxyInvokeRequest request, InvokeContext context) {
        if (!request.hasTarget()) {
            throw new IllegalArgumentException(
                    "Provide endpointId, or both method and path (legacy field name uri is accepted as path)");
        }
        if (context == InvokeContext.RUNTIME) {
            StrictEndpointsEnforcer.requireEndpointIdForManaged(code3rd, request);
        }
        rateLimiter.checkAllowed(code3rd);
        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                registry.require(code3rd),
                request.getEndpointId(),
                request.getMethod(),
                request.effectivePath());
        return doInvoke(code3rd, resolved, request.getQuery(), request.getHeaders(), request.getBody(), context);
    }

    public ProxyInvokeResponse invokeEndpoint(String code3rd, String endpointId, EndpointInvokeRequest request) {
        return invokeEndpoint(code3rd, endpointId, request, InvokeContext.RUNTIME);
    }

    public void streamEndpoint(
            String code3rd, String endpointId, EndpointInvokeRequest request, StreamingInvocationSink sink) {
        streamEndpoint(code3rd, endpointId, request, InvokeContext.RUNTIME, sink);
    }

    public void streamEndpoint(
            String code3rd,
            String endpointId,
            EndpointInvokeRequest request,
            InvokeContext context,
            StreamingInvocationSink sink) {
        if (context == InvokeContext.RUNTIME) {
            StrictEndpointsEnforcer.requireEndpointIdForManaged(code3rd, endpointId);
        }
        rateLimiter.checkAllowed(code3rd);
        ConnectorSpec spec = registry.require(code3rd);
        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(spec, endpointId, null, null);
        EndpointInvokeRequest safe = request != null ? request : new EndpointInvokeRequest();
        Map<String, String> headers = safe.getHeaders() != null ? new java.util.LinkedHashMap<>(safe.getHeaders()) : new java.util.LinkedHashMap<>();
        headers.putIfAbsent("Accept", "text/event-stream");
        doStream(code3rd, resolved, safe.getQuery(), headers, safe.getBody(), context, sink);
    }

    private void doStream(
            String code3rd,
            EndpointResolver.ResolvedInvocation resolved,
            java.util.Map<String, String> query,
            java.util.Map<String, String> headers,
            String body,
            InvokeContext context,
            StreamingInvocationSink delegate) {
        long start = System.currentTimeMillis();
        InvocationRequest.HttpMethod method = InvocationRequest.HttpMethod.valueOf(resolved.method());
        StreamingInvocationSink auditing = new StreamingInvocationSink() {
            @Override
            public void writeLine(String line) {
                delegate.writeLine(line);
            }

            @Override
            public void onVendorResponse(int httpStatus, java.util.Map<String, String> responseHeaders) {
                delegate.onVendorResponse(httpStatus, responseHeaders);
            }

            @Override
            public void fail(Throwable error) {
                delegate.fail(error);
                logStreamAudit(code3rd, resolved, false, 0, start, context);
            }

            @Override
            public void complete() {
                delegate.complete();
                logStreamAudit(code3rd, resolved, true, 200, start, context);
            }
        };
        orchestrator.invokeStream(
                new InvocationRequest(
                        new ConnectorCode(code3rd),
                        resolved.endpointId(),
                        method,
                        resolved.path(),
                        query,
                        headers,
                        body,
                        InvocationRequest.InvocationMode.SSE),
                auditing);
    }

    private void logStreamAudit(
            String code3rd,
            EndpointResolver.ResolvedInvocation resolved,
            boolean success,
            int vendorStatus,
            long start,
            InvokeContext context) {
        if (!invokeProperties.isAuditEnabled()) {
            return;
        }
        auditLogger.log(new InvokeAuditEvent(
                code3rd,
                resolved.endpointId(),
                resolved.method(),
                resolved.path(),
                success,
                vendorStatus,
                System.currentTimeMillis() - start,
                clientAddress(),
                context.name() + "_STREAM"));
    }

    public ProxyInvokeResponse invokeEndpoint(
            String code3rd, String endpointId, EndpointInvokeRequest request, InvokeContext context) {
        if (context == InvokeContext.RUNTIME) {
            StrictEndpointsEnforcer.requireEndpointIdForManaged(code3rd, endpointId);
        }
        rateLimiter.checkAllowed(code3rd);
        ConnectorSpec spec = registry.require(code3rd);
        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(spec, endpointId, null, null);
        EndpointInvokeRequest safe = request != null ? request : new EndpointInvokeRequest();
        return doInvoke(code3rd, resolved, safe.getQuery(), safe.getHeaders(), safe.getBody(), context);
    }

    private ProxyInvokeResponse doInvoke(
            String code3rd,
            EndpointResolver.ResolvedInvocation resolved,
            java.util.Map<String, String> query,
            java.util.Map<String, String> headers,
            String body,
            InvokeContext context) {
        long start = System.currentTimeMillis();
        InvocationRequest.HttpMethod method = InvocationRequest.HttpMethod.valueOf(resolved.method());
        InvocationResult result = orchestrator.invoke(new InvocationRequest(
                new ConnectorCode(code3rd),
                resolved.endpointId(),
                method,
                resolved.path(),
                query,
                headers,
                body,
                InvocationRequest.InvocationMode.SYNC));
        int vendorStatus = result.httpStatus();
        ProxyInvokeResponse response = ProxyInvokeResponse.builder()
                .success(result.success())
                .vendorHttpStatus(vendorStatus)
                .httpStatus(invokeProperties.resolveHttpStatus(vendorStatus, result.success()))
                .vendorCode(result.vendorCode())
                .vendorMessage(result.vendorMessage())
                .data(result.parsedData())
                .rawBody(result.rawBody())
                .rawBodyEncoding(result.rawBodyEncoding())
                .latencyMillis(result.latencyMillis())
                .endpointId(resolved.endpointId())
                .method(resolved.method())
                .path(resolved.path())
                .build();
        if (invokeProperties.isAuditEnabled()) {
            auditLogger.log(new InvokeAuditEvent(
                    code3rd,
                    resolved.endpointId(),
                    resolved.method(),
                    resolved.path(),
                    response.isSuccess(),
                    response.getVendorHttpStatus(),
                    System.currentTimeMillis() - start,
                    clientAddress(),
                    context.name()));
        }
        return response;
    }

    private static String clientAddress() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
