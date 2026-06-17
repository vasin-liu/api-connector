/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping.exception;

/**
 * Platform mapping error codes (D-14).
 */
public enum MappingErrorCode {

    MAPPING_SPEC_INVALID,
    MAPPING_SCRIPT_COMPILE_ERROR,
    MAPPING_SCRIPT_RUNTIME_ERROR,
    MAPPING_COERCE_FAILED,
    TRANSFORM_UNSUPPORTED,
    TRANSFORM_KEY_MISSING,
    TRANSFORM_FAILED
}
