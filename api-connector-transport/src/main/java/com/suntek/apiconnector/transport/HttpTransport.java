/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.transport;

/**
 * Outbound HTTP port. Implementations must not classify Challenge.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public interface HttpTransport {

    /**
     * @param request outbound request
     * @param context timeouts and cancellation
     * @return raw response; {@code completed=false} means UNKNOWN_OUTCOME
     */
    RawHttpResponse execute(RawHttpRequest request, TransportContext context);
}
