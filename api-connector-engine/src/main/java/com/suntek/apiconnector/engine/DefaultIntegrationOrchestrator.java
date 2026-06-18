/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.AuthEngine;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.AuthOutcome;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.transport.HttpStreamHandler;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.HttpTransportRequest;
import com.suntek.apiconnector.engine.transport.HttpTransportResponse;
import com.suntek.apiconnector.mapping.ErrorMappingTrigger;
import com.suntek.apiconnector.mapping.ResolvedMapping;
import com.suntek.apiconnector.mapping.TransformPipeline;
import com.suntek.apiconnector.mapping.spi.MappingEngine;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认编排器：Spec → Auth → HTTP → 响应映射。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public class DefaultIntegrationOrchestrator implements IntegrationOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultIntegrationOrchestrator.class);

    private final ConnectorRegistry registry;
    private final AuthEngine authEngine;
    private final HttpTransport httpTransport;
    private final ResponseEvaluator responseEvaluator;
    private final MappingEngine mappingEngine;
    private final TransformPipeline transformPipeline;
    private final ResolvedMappingCache resolvedMappingCache;
    private final boolean mappingEnabled;

    /**
     * 构造编排器（兼容旧签名，无映射/转换，整链直通）。
     *
     * @param registry           连接器注册表
     * @param authEngine         认证引擎
     * @param httpTransport      HTTP 传输
     * @param responseEvaluator  响应判定
     */
    public DefaultIntegrationOrchestrator(
            ConnectorRegistry registry,
            AuthEngine authEngine,
            HttpTransport httpTransport,
            ResponseEvaluator responseEvaluator) {
        this(registry, authEngine, httpTransport, responseEvaluator, null, null, null, false);
    }

    /**
     * 构造编排器，装配映射与转换流水线（MAP-06 / PIPE-01）。
     *
     * @param registry             连接器注册表
     * @param authEngine           认证引擎
     * @param httpTransport        HTTP 传输
     * @param responseEvaluator    响应判定
     * @param mappingEngine        映射引擎（请求/响应/错误），可为 {@code null} 表示直通
     * @param transformPipeline    转换流水线（SM4 等），可为 {@code null} 表示直通
     * @param resolvedMappingCache 已解析映射缓存（D-21），可为 {@code null} 表示直通
     * @param mappingEnabled       全局映射开关（D-22）；{@code false} 时整链直通
     */
    public DefaultIntegrationOrchestrator(
            ConnectorRegistry registry,
            AuthEngine authEngine,
            HttpTransport httpTransport,
            ResponseEvaluator responseEvaluator,
            MappingEngine mappingEngine,
            TransformPipeline transformPipeline,
            ResolvedMappingCache resolvedMappingCache,
            boolean mappingEnabled) {
        this.registry = registry;
        this.authEngine = authEngine;
        this.httpTransport = httpTransport;
        this.responseEvaluator = responseEvaluator;
        this.mappingEngine = mappingEngine;
        this.transformPipeline = transformPipeline;
        this.resolvedMappingCache = resolvedMappingCache;
        this.mappingEnabled = mappingEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InvocationResult invoke(InvocationRequest request) {
        long start = System.currentTimeMillis();
        ConnectorSpec spec = registry.require(request.connectorCode().value());
        Map<String, String> credentials = registry.credentials(request.connectorCode().value());

        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                spec,
                request.endpointId(),
                request.method().name(),
                request.path());
        EndpointSpec endpointSpec = EndpointResolver.endpoint(spec, request.endpointId());

        // D-22: global toggle off OR back-compat passthrough wiring => full passthrough.
        boolean pipelineActive = mappingEnabled
                && mappingEngine != null
                && transformPipeline != null
                && resolvedMappingCache != null;
        // D-04: a null endpointSpec (e.g. legacy raw-path dispatch) has no resolvable mapping key,
        // so it stays a mapping passthrough; transforms still apply via spec.transform().
        ResolvedMapping resolvedMapping = (pipelineActive && endpointSpec != null)
                ? resolvedMappingCache.get(spec, endpointSpec)
                : null;
        // D-04: passthrough endpoints (no configured mapping) never touch the mapping engine.
        boolean mappingActive = pipelineActive && MappingConfigResolver.hasAnyMapping(resolvedMapping);

        // REQUEST SIDE (D-01 / MAP-06): mapRequest -> transform.applyRequest -> auth signs the final body.
        String outboundBody = request.body();
        if (mappingActive) {
            long t = System.currentTimeMillis();
            MappingContext reqCtx = new MappingContext(
                    spec.code3rd(),
                    MappingDirection.REQUEST,
                    outboundBody,
                    null,
                    new EndpointMeta(request.endpointId(), resolved.method(), resolved.path()));
            outboundBody = mappingEngine.mapRequest(reqCtx, resolvedMapping);
            logStage("mapRequest", t);
        }
        if (pipelineActive) {
            long t = System.currentTimeMillis();
            outboundBody = transformPipeline.applyRequest(outboundBody, spec.transform(), credentials);
            logStage("transformRequest", t);
        }

        long authStart = System.currentTimeMillis();
        AuthenticatedInvocation auth = authenticate(
                spec, resolved, endpointSpec, credentials, request.query(), outboundBody);
        logStage("auth", authStart);

        long httpStart = System.currentTimeMillis();
        HttpTransportResponse httpResp = httpTransport.exchange(new HttpTransportRequest(
                spec.baseUrl(),
                resolved.method(),
                resolved.path(),
                auth.query(),
                auth.headers(),
                auth.body(),
                spec.transport()));
        logStage("http", httpStart);

        // RESPONSE SIDE (D-02 strict mirror): transform.applyResponse -> evaluate -> mapResponse/mapError.
        String decoded = httpResp.body();
        if (pipelineActive) {
            long t = System.currentTimeMillis();
            decoded = transformPipeline.applyResponse(httpResp.body(), spec.transform(), credentials);
            logStage("transformResponse", t);
        }

        ResponseEvaluation evaluation = responseEvaluator.evaluate(spec.response(), decoded);

        String finalBody = decoded;
        if (mappingActive) {
            ErrorMappingTrigger trigger = new ErrorMappingTrigger(httpResp.statusCode(), evaluation.success());
            boolean mapErr = ErrorMappingTrigger.shouldMapError(trigger) && resolvedMapping.hasError();
            MappingContext respCtx = new MappingContext(
                    spec.code3rd(),
                    mapErr ? MappingDirection.ERROR : MappingDirection.RESPONSE,
                    decoded,
                    auth.snapshot(),
                    new EndpointMeta(request.endpointId(), resolved.method(), resolved.path()));
            if (mapErr) {
                long t = System.currentTimeMillis();
                finalBody = mappingEngine.mapError(respCtx, resolvedMapping, trigger);
                logStage("mapError", t);
            } else if (evaluation.success()) {
                long t = System.currentTimeMillis();
                finalBody = mappingEngine.mapResponse(respCtx, resolvedMapping);
                logStage("mapResponse", t);
            }
        }

        return new InvocationResult(
                httpResp.statusCode(),
                evaluation.success(),
                evaluation.vendorCode(),
                evaluation.vendorMessage(),
                finalBody,
                httpResp.bodyEncoding(),
                evaluation.parsedData(),
                System.currentTimeMillis() - start,
                httpResp.headers(),
                auth.snapshot(),
                auth.outcome());
    }

    /**
     * Emits a DEBUG-only stage timing line (D-23, Security V7): stage name + elapsed millis only,
     * never request/response bodies or credentials.
     *
     * @param stage       pipeline stage name
     * @param startMillis stage start timestamp in millis
     */
    private void logStage(String stage, long startMillis) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("stage={} ms={}", stage, System.currentTimeMillis() - startMillis);
        }
    }

    @Override
    public void invokeStream(InvocationRequest request, StreamingInvocationSink sink) {
        ConnectorSpec spec = registry.require(request.connectorCode().value());
        Map<String, String> credentials = registry.credentials(request.connectorCode().value());

        EndpointResolver.ResolvedInvocation resolved = EndpointResolver.resolve(
                spec,
                request.endpointId(),
                request.method().name(),
                request.path());
        EndpointSpec endpointSpec = EndpointResolver.endpoint(spec, request.endpointId());

        Map<String, String> headers = new HashMap<>();
        if (request.headers() != null) {
            headers.putAll(request.headers());
        }
        if (!headers.containsKey("Accept") && !headers.containsKey("accept")) {
            headers.put("Accept", "text/event-stream");
        }

        AuthenticatedInvocation auth = authenticate(
                spec, resolved, endpointSpec, credentials, request.query(), request.body());
        headers.putAll(auth.headers());

        try {
            httpTransport.exchangeStream(
                    new HttpTransportRequest(
                            spec.baseUrl(),
                            resolved.method(),
                            resolved.path(),
                            auth.query(),
                            headers,
                            auth.body(),
                            spec.transport()),
                    new HttpStreamHandler() {
                        private boolean headersSent;

                        @Override
                        public void onLine(int statusCode, Map<String, String> responseHeaders, String line) {
                            if (!headersSent) {
                                sink.onVendorResponse(statusCode, responseHeaders);
                                headersSent = true;
                            }
                            if (line != null) {
                                sink.writeLine(line);
                            } else {
                                sink.complete();
                            }
                        }
                    });
        } catch (Exception e) {
            sink.fail(e);
            throw e;
        }
    }

    private AuthenticatedInvocation authenticate(
            ConnectorSpec spec,
            EndpointResolver.ResolvedInvocation resolved,
            EndpointSpec endpointSpec,
            Map<String, String> credentials,
            Map<String, String> requestQuery,
            String requestBody) {
        Map<String, Object> authConfig = AuthConfigResolver.resolve(spec, endpointSpec);
        Map<String, Object> ext = new HashMap<>();
        AuthContext authContext = new AuthContext(
                spec.code3rd(),
                spec.baseUrl(),
                resolved.method(),
                resolved.path(),
                requestQuery != null ? requestQuery : Map.of(),
                requestBody,
                authConfig,
                credentials,
                ext);
        AuthOutcome authOutcome = authEngine.authenticate(authContext);
        List<String> profileTypes = AuthContextSnapshots.profileTypesFrom(authConfig);
        AuthContextSnapshot snapshot = AuthContextSnapshots.from(authContext, authOutcome, profileTypes);

        Map<String, String> headers = new HashMap<>();
        headers.putAll(authOutcome.headers());

        Map<String, String> query = new HashMap<>();
        if (requestQuery != null) {
            query.putAll(requestQuery);
        }
        query.putAll(authOutcome.query());

        String body = authOutcome.mutatedBody() != null ? authOutcome.mutatedBody() : requestBody;
        return new AuthenticatedInvocation(snapshot, authOutcome, headers, query, body);
    }

    private record AuthenticatedInvocation(
            AuthContextSnapshot snapshot,
            AuthOutcome outcome,
            Map<String, String> headers,
            Map<String, String> query,
            String body) {
    }
}
