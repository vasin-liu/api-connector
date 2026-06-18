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
import com.suntek.apiconnector.auth.exception.AuthException;
import com.suntek.apiconnector.domain.model.ConnectorCode;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.engine.EndpointResolver;
import com.suntek.apiconnector.mapping.exception.MappingException;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 运行时 Invoke 编排（endpointId 解析 + Orchestrator）。
 */
@Service
public class IntegrationInvokeService {

    /** MDC 关联 id 键（全代码库首个 MDC 用途，D-11）。 */
    private static final String MDC_REQUEST_ID = "requestId";

    /** 入站关联 id 最大长度（防日志注入，ASVS V7）。 */
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

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
        String correlationId = resolveCorrelationId();
        InvocationRequest.HttpMethod method = InvocationRequest.HttpMethod.valueOf(resolved.method());
        try {
            MDC.put(MDC_REQUEST_ID, correlationId);
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
                    logStreamAudit(code3rd, resolved, false, 0, start, context, correlationId, "VENDOR_ERROR");
                }

                @Override
                public void complete() {
                    delegate.complete();
                    logStreamAudit(code3rd, resolved, true, 200, start, context, correlationId, "SUCCESS");
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
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void logStreamAudit(
            String code3rd,
            EndpointResolver.ResolvedInvocation resolved,
            boolean success,
            int vendorStatus,
            long start,
            InvokeContext context,
            String correlationId,
            String outcome) {
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
                context.name() + "_STREAM",
                correlationId,
                outcome));
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
        String correlationId = resolveCorrelationId();
        InvocationRequest.HttpMethod method = InvocationRequest.HttpMethod.valueOf(resolved.method());
        try {
            MDC.put(MDC_REQUEST_ID, correlationId);
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
                        context.name(),
                        correlationId,
                        classifyOutcome(response.isSuccess(), response.getVendorHttpStatus())));
            }
            return response;
        } catch (MappingException | AuthException ex) {
            // D-19/D-20: request-side pipeline failure — audit as PIPELINE_ERROR(<stage>) with
            // vendorHttpStatus=0, then rethrow so RuntimeApiExceptionHandler renders the body.
            if (invokeProperties.isAuditEnabled()) {
                auditLogger.log(new InvokeAuditEvent(
                        code3rd,
                        resolved.endpointId(),
                        resolved.method(),
                        resolved.path(),
                        false,
                        0,
                        System.currentTimeMillis() - start,
                        clientAddress(),
                        context.name(),
                        correlationId,
                        pipelineOutcome(ex)));
            }
            throw ex;
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /**
     * 区分审计 outcome（D-14）：{@code SUCCESS} 当业务成功且厂家状态 &lt; 400；否则 {@code VENDOR_ERROR}。
     *
     * @param success          业务是否成功
     * @param vendorHttpStatus 厂家 HTTP 状态
     * @return outcome 字符串
     */
    private static String classifyOutcome(boolean success, int vendorHttpStatus) {
        return success && vendorHttpStatus < 400 ? "SUCCESS" : "VENDOR_ERROR";
    }

    /**
     * 管线异常 outcome（D-14/D-19）：{@code PIPELINE_ERROR(<stage>)}，stage 来自异常类型。
     *
     * @param ex 映射/转换或认证管线异常
     * @return {@code PIPELINE_ERROR(<stage>)}
     */
    private static String pipelineOutcome(RuntimeException ex) {
        String stage;
        if (ex instanceof MappingException mapping) {
            stage = mapping.code() != null && mapping.code().name().startsWith("TRANSFORM") ? "transform" : "mapping";
        } else {
            stage = "auth";
        }
        return "PIPELINE_ERROR(" + stage + ")";
    }

    /**
     * 解析关联 id（D-10）：优先 {@code X-Request-Id}，回退 {@code X-Trace-Id}，均缺失时生成服务端 UUID。
     * 头部来源的值在使用前经 {@link #sanitizeCorrelationId(String)} 净化（防日志注入）。
     *
     * @return 净化后的关联 id（绝不为 null/空）
     */
    static String resolveCorrelationId() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String requestId = request.getHeader("X-Request-Id");
            if (requestId != null && !requestId.isBlank()) {
                return sanitizeCorrelationId(requestId);
            }
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId != null && !traceId.isBlank()) {
                return sanitizeCorrelationId(traceId);
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 净化入站关联 id：剥离 {@code \r}/{@code \n}（防日志伪造），并截断到 {@value #MAX_CORRELATION_ID_LENGTH} 字符。
     *
     * @param raw 原始头部值
     * @return 净化后的值（绝不为 null）
     */
    static String sanitizeCorrelationId(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.replace("\r", "").replace("\n", "");
        if (stripped.length() > MAX_CORRELATION_ID_LENGTH) {
            stripped = stripped.substring(0, MAX_CORRELATION_ID_LENGTH);
        }
        return stripped;
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
