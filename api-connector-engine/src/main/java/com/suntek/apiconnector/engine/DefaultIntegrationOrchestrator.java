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
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.transport.HttpStreamHandler;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.HttpTransportRequest;
import com.suntek.apiconnector.engine.transport.HttpTransportResponse;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import com.suntek.apiconnector.spec.model.EndpointSpec;

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

    private final ConnectorRegistry registry;
    private final AuthEngine authEngine;
    private final HttpTransport httpTransport;
    private final ResponseEvaluator responseEvaluator;

    /**
     * 构造编排器。
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
        this.registry = registry;
        this.authEngine = authEngine;
        this.httpTransport = httpTransport;
        this.responseEvaluator = responseEvaluator;
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
        AuthenticatedInvocation auth = authenticate(
                spec, resolved, endpointSpec, credentials, request.query(), request.body());

        Map<String, String> headers = new HashMap<>();
        if (request.headers() != null) {
            headers.putAll(request.headers());
        }
        headers.putAll(auth.headers());

        HttpTransportResponse httpResp = httpTransport.exchange(new HttpTransportRequest(
                spec.baseUrl(),
                resolved.method(),
                resolved.path(),
                auth.query(),
                auth.headers(),
                auth.body(),
                spec.transport()));

        ResponseEvaluation evaluation = responseEvaluator.evaluate(spec.response(), httpResp.body());
        return new InvocationResult(
                httpResp.statusCode(),
                evaluation.success(),
                evaluation.vendorCode(),
                evaluation.vendorMessage(),
                httpResp.body(),
                httpResp.bodyEncoding(),
                evaluation.parsedData(),
                System.currentTimeMillis() - start,
                httpResp.headers(),
                auth.snapshot(),
                auth.outcome());
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
