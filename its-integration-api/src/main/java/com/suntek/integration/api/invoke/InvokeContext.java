/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.invoke;

/**
 * 调用场景：决定 strictEndpoints、限流等策略。
 */
public enum InvokeContext {
    /** 运行时公开 API */
    RUNTIME,
    /** 管理端试调（可 method+path） */
    ADMIN_TRIAL,
    /** 旧 thirdpart URL 兼容转发 */
    LEGACY
}
