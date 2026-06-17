/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.spi;

import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.mapping.ResolvedMapping;

/**
 * Request/response/error mapping facade (D-03, D-10).
 */
public interface MappingEngine {

    /**
     * Maps outbound request body using resolved request direction config.
     */
    String mapRequest(MappingContext ctx, ResolvedMapping config);

    /**
     * Maps successful response body using resolved response direction config.
     */
    String mapResponse(MappingContext ctx, ResolvedMapping config);

    /**
     * Maps error response body using resolved error direction config.
     * Error trigger evaluation is deferred to Plan 05 (D-15).
     */
    String mapError(MappingContext ctx, ResolvedMapping config);
}
