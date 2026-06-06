/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.api.admin;

import lombok.Data;

import java.util.Map;

/**
 * 保存/发布连接器配置请求。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Data
public class SaveConnectorConfigRequest {

    /** ConnectorSpec JSON 同构 Map。 */
    private Map<String, Object> spec;

    /** 凭证补丁：null 表示不修改该字段。 */
    private Map<String, String> credentials;

    /** true 表示发布，false 表示草稿。 */
    private boolean publish;
}
