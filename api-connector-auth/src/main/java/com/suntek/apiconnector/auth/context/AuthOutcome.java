/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.auth.context;

import java.util.Map;

/**
 * Authentication outcome: headers/query/body mutations for HTTP transport.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class AuthOutcome {

    private final Map<String, String> headers;
    private final Map<String, String> query;
    private final String mutatedBody;

    public AuthOutcome(Map<String, String> headers, Map<String, String> query, String mutatedBody) {
        this.headers = headers;
        this.query = query;
        this.mutatedBody = mutatedBody;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Map<String, String> query() {
        return query;
    }

    public String mutatedBody() {
        return mutatedBody;
    }

    /**
     * Empty outcome.
     *
     * @return empty outcome
     */
    public static AuthOutcome empty() {
        return new AuthOutcome(Map.of(), Map.of(), null);
    }
}
