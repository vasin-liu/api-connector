/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.transport;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound HTTP request.
 *
 * @param method  HTTP method
 * @param uri     target
 * @param headers headers
 * @param body    optional body
 * @author Gensokyo
 * @since 2026-09-14
 */
public record RawHttpRequest(
        String method,
        URI uri,
        Map<String, List<String>> headers,
        Optional<byte[]> body
) {
}
