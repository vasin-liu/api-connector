/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.api.legacy;

/**
 * 旧 thirdpart 响应体形态。
 */
public enum LegacyResponseStyle {

    /** 透传厂家 JSON（IDPS 等已是 IdpsResult 形态） */
    VENDOR_RAW,

    /** 包装为 suntek {@code Result} 形态：code / message / data / success */
    SUNTEK_RESULT
}
