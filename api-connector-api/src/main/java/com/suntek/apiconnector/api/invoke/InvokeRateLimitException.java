/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.invoke;

/**
 * 触发按连接器限流。
 */
public class InvokeRateLimitException extends RuntimeException {

    public InvokeRateLimitException(String message) {
        super(message);
    }
}
