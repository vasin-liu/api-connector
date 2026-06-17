/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.domain.spi;

import com.suntek.apiconnector.domain.model.InvocationRequest;
import com.suntek.apiconnector.domain.model.InvocationResult;
import com.suntek.apiconnector.domain.spi.StreamingInvocationSink;

/**
 * 对接编排入口：鉴权、传输、映射、统一结果。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public interface IntegrationOrchestrator {

    /**
     * 执行一次第三方调用。
     *
     * @param request 调用请求
     * @return 调用结果
     */
    InvocationResult invoke(InvocationRequest request);

    /**
     * 流式调用（SSE / NDJSON）：将厂家响应按行写入 sink。
     *
     * @param request 调用请求（mode 应为 {@link InvocationRequest.InvocationMode#SSE}）
     * @param sink    流式输出
     */
    void invokeStream(InvocationRequest request, StreamingInvocationSink sink);
}
