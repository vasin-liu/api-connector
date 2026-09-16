/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

/**
 * Replay target identity. Runtime re-renders from the plan; it MUST NOT clone a prior RawHttpRequest.
 *
 * @param requestId request id to re-render
 * @author Gensokyo
 * @since 2026-09-15
 */
public record OriginalRequestTemplate(String requestId) {

    /**
     * @param request compiled request
     * @return template identity
     */
    public static OriginalRequestTemplate of(CompiledRequest request) {
        return new OriginalRequestTemplate(request.requestId());
    }
}
