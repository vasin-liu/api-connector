/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine;

/**
 * 厂家响应解析结果。
 *
 * @param success        业务是否成功
 * @param vendorCode     厂家业务码
 * @param vendorMessage  厂家消息
 * @param parsedData     dataPath 提取的数据
 */
public record ResponseEvaluation(
        boolean success, String vendorCode, String vendorMessage, Object parsedData) {

    public static ResponseEvaluation emptySuccess() {
        return new ResponseEvaluation(true, null, null, null);
    }
}
