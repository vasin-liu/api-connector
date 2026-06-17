/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors.baidu;

import com.suntek.apiconnector.spec.catalog.CatalogAuth;
import com.suntek.apiconnector.spec.catalog.CatalogConnector;
import com.suntek.apiconnector.spec.catalog.CatalogResponse;
import com.suntek.apiconnector.spec.catalog.HttpPost;

@CatalogConnector(code3rd = "BAIDU_WENXIN", baseUrl = "https://aip.baidubce.com")
@CatalogAuth(
        type = "oauth2_token_in_query",
        tokenUrl = "/oauth/2.0/token",
        clientIdRef = "appId",
        clientSecretRef = "appSecret",
        tokenParam = "access_token")
@CatalogResponse(dataPath = "$.result")
public interface BaiduWenxinConnectorCatalog {

    @HttpPost("/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro")
    void chatCompletionsPro();

    @HttpPost("/rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/embedding-v1")
    void embeddingV1();
}
