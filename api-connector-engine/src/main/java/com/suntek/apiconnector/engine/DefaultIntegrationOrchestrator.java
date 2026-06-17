/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.AuthEngine;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.context.AuthOutcome;
import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.IntegrationOrchestrator;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;
import com.suntek.apiconnector.engine.transport.HttpStreamHandler;
import com.suntek.apiconnector.engine.transport.HttpTransport;
import com.suntek.apiconnector.engine.transport.HttpTransportRequest;
import com.suntek.apiconnector.engine.transport.HttpTransportResponse;
import com.suntek.apiconnector.spec.model.ConnectorSpec;

import java.util.HashMap;
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

        AuthContext authContext = new AuthContext(
                request.connectorCode().value(),
                spec.baseUrl(),
                request.method().name(),
                request.path(),
                request.query() != null ? request.query() : Map.of(),
                request.body(),
                spec.auth(),
                credentials,
                Map.of());
        AuthOutcome authOutcome = authEngine.authenticate(authContext);

        Map<String, String> headers = new HashMap<>();
        if (request.headers() != null) {
            headers.putAll(request.headers());
        }
        headers.putAll(authOutcome.headers());

        Map<String, String> query = new HashMap<>();
        if (request.query() != null) {
            query.putAll(request.query());
        }
        query.putAll(authOutcome.query());

        String body = authOutcome.mutatedBody() != null ? authOutcome.mutatedBody() : request.body();
        HttpTransportResponse httpResp = httpTransport.exchange(new HttpTransportRequest(
                spec.baseUrl(),
                request.method().name(),
                request.path(),
                query,
                headers,
                body,
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
                httpResp.headers());
    }

    @Override
    public void invokeStream(InvocationRequest request, StreamingInvocationSink sink) {
        ConnectorSpec spec = registry.require(request.connectorCode().value());
        Map<String, String> credentials = registry.credentials(request.connectorCode().value());

        Map<String, String> headers = new HashMap<>();
        if (request.headers() != null) {
            headers.putAll(request.headers());
        }
        if (!headers.containsKey("Accept") && !headers.containsKey("accept")) {
            headers.put("Accept", "text/event-stream");
        }

        AuthContext authContext = new AuthContext(
                request.connectorCode().value(),
                spec.baseUrl(),
                request.method().name(),
                request.path(),
                request.query() != null ? request.query() : Map.of(),
                request.body(),
                spec.auth(),
                credentials,
                Map.of());
        AuthOutcome authOutcome = authEngine.authenticate(authContext);
        headers.putAll(authOutcome.headers());

        Map<String, String> query = new HashMap<>();
        if (request.query() != null) {
            query.putAll(request.query());
        }
        query.putAll(authOutcome.query());

        String body = authOutcome.mutatedBody() != null ? authOutcome.mutatedBody() : request.body();
        try {
            httpTransport.exchangeStream(
                    new HttpTransportRequest(
                            spec.baseUrl(),
                            request.method().name(),
                            request.path(),
                            query,
                            headers,
                            body,
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
}
