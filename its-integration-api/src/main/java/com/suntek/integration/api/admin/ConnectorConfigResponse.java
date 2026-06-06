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
 * 连接器完整配置（Spec + 脱敏凭证 + 状态）。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Data
@AllArgsConstructor
public class ConnectorConfigResponse {

    private String code3rd;
    private String specStatus;
    private String authSummary;
    private Map<String, Object> spec;
    private CredentialsView credentials;
    /** 内置 Catalog 受管：端点不可在控制台修改 */
    private boolean catalogManaged;
}
