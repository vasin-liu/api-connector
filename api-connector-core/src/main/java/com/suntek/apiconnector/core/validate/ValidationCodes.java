/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.validate;

/**
 * DefinitionValidator codes from {@code 07-plan-compiler.md} table U.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class ValidationCodes {

    public static final String VAL_JSONPATH_FORBIDDEN = "VAL_JSONPATH_FORBIDDEN";
    public static final String VAL_EMPTY_ALL = "VAL_EMPTY_ALL";
    public static final String VAL_EMPTY_ANY = "VAL_EMPTY_ANY";
    public static final String VAL_STATUS_RANGE = "VAL_STATUS_RANGE";
    public static final String VAL_STEP_ID_MISSING = "VAL_STEP_ID_MISSING";
    public static final String VAL_GLOBAL_WRITE = "VAL_GLOBAL_WRITE";
    public static final String VAL_AUTH_FLOW_REQUIRED = "VAL_AUTH_FLOW_REQUIRED";
    public static final String VAL_THEN_MISSING = "VAL_THEN_MISSING";
    public static final String VAL_THEN_NOT_ALLOWED = "VAL_THEN_NOT_ALLOWED";
    public static final String VAL_WHEN_MISSING = "VAL_WHEN_MISSING";
    public static final String VAL_NOT_ARITY = "VAL_NOT_ARITY";
    public static final String VAL_CONDITION_DEPTH = "VAL_CONDITION_DEPTH";
    public static final String VAL_AUTH_ON_EMPTY = "VAL_AUTH_ON_EMPTY";
    public static final String VAL_FLOW_BUSINESS_MISSING = "VAL_FLOW_BUSINESS_MISSING";
    public static final String VAL_INPUT_SCOPE = "VAL_INPUT_SCOPE";
    public static final String VAL_SECRET_LITERAL = "VAL_SECRET_LITERAL";
    public static final String VAL_PIPE_CYCLE = "VAL_PIPE_CYCLE";
    public static final String VAL_PIPE_TYPE = "VAL_PIPE_TYPE";
    public static final String VAL_PIPE_PORT_REQUIRED = "VAL_PIPE_PORT_REQUIRED";
    public static final String VAL_PIPE_UNKNOWN_NODE = "VAL_PIPE_UNKNOWN_NODE";
    public static final String VAL_PIPE_CARDINALITY = "VAL_PIPE_CARDINALITY";

    private ValidationCodes() {
    }
}
