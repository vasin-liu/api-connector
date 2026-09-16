/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Compiled outbound request template.
 *
 * @param requestId     request id
 * @param method        HTTP method
 * @param url           URL template
 * @param headers       header bindings
 * @param query         query bindings
 * @param body          optional body binding
 * @param replay        replay policy
 * @param declaredSinks secret sinks
 * @param cookies       fromStore, acceptSetCookie, or empty
 * @author Gensokyo
 * @since 2026-09-14
 */
public record CompiledRequest(
        String requestId,
        String method,
        UrlTemplate url,
        List<NamedBinding> headers,
        List<NamedBinding> query,
        Optional<Object> body,
        ReplayPolicy replay,
        EnumSet<SecretSink> declaredSinks,
        Optional<String> cookies
) {
}
