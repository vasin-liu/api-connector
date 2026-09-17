/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

/**
 * Execute-time rejection that is not a YAML validation error.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class ExecuteException extends IllegalStateException {

    public static final String PLAN_CAPABILITY_UNSUPPORTED = "PLAN_CAPABILITY_UNSUPPORTED";
    public static final String DEFINITION_NOT_PUBLISHED = "DEFINITION_NOT_PUBLISHED";
    public static final String DEFINITION_NOT_FOUND = "DEFINITION_NOT_FOUND";
    public static final String SECRET_UNRESOLVABLE = "SECRET_UNRESOLVABLE";
    public static final String SECRET_SINK_DENIED = "SECRET_SINK_DENIED";

    private final String code;

    /**
     * @param code    stable execute code
     * @param message detail
     */
    public ExecuteException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    /**
     * @return stable code
     */
    public String code() {
        return code;
    }
}
