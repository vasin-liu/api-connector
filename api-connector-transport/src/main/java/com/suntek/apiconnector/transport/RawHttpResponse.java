/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.transport;

import com.suntek.apiconnector.core.http.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * Outbound HTTP response.
 *
 * @param status    HTTP status if completed
 * @param headers   response headers
 * @param body      body
 * @param completed false when written then dropped (UNKNOWN_OUTCOME)
 * @author Gensokyo
 * @since 2026-09-14
 */
public record RawHttpResponse(
        int status,
        Map<String, List<String>> headers,
        ResponseBody body,
        boolean completed
) {
}
