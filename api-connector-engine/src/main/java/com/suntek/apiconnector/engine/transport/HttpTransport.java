/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.engine.transport;

/**
 * HTTP 传输抽象（JDK HttpClient + 虚拟线程实现）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public interface HttpTransport {

    /**
     * 发起 HTTP 交换。
     *
     * @param request 传输请求
     * @return 传输响应
     */
    HttpTransportResponse exchange(HttpTransportRequest request);

    /**
     * 流式 HTTP 交换：按行回调响应体（适用于 text/event-stream、NDJSON）。
     *
     * @param request 传输请求
     * @param handler 行级回调；结束时 {@code onLine(..., null)}
     */
    void exchangeStream(HttpTransportRequest request, HttpStreamHandler handler);
}
