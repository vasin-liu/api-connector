/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 管理控制台 — 连接器列表项（不含凭证明文）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Data
@AllArgsConstructor
public class ConnectorSummaryResponse {

    private String code3rd;
    private String version;
    private String baseUrl;
    private String protocol;
    private String authType;
    private int endpointCount;
    private Map<String, Object> spec;
    private String specStatus;
    /** 内置 Catalog 受管 */
    private boolean catalogManaged;
}
