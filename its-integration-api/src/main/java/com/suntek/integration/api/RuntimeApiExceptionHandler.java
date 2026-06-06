/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api;

import com.suntek.integration.api.dto.ApiErrorResponse;
import com.suntek.integration.api.invoke.InvokeRateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 运行时 Invoke API 异常 → 统一 JSON。
 */
@RestControllerAdvice(basePackageClasses = com.suntek.integration.api.controller.IntegrationProxyController.class)
public class RuntimeApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String code = "BAD_REQUEST";
        if (ex.getMessage() != null && ex.getMessage().startsWith("Unknown connector")) {
            status = HttpStatus.NOT_FOUND;
            code = "CONNECTOR_NOT_FOUND";
        } else if (ex.getMessage() != null && ex.getMessage().contains("strictEndpoints")) {
            code = "STRICT_ENDPOINTS";
        } else if (ex.getMessage() != null && ex.getMessage().contains("endpoint")) {
            code = "ENDPOINT_NOT_FOUND";
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
                .code(code)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(InvokeRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> rateLimited(InvokeRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiErrorResponse.builder()
                .code("RATE_LIMITED")
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> failedDependency(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiErrorResponse.builder()
                .code("UPSTREAM_AUTH_FAILED")
                .message(ex.getMessage())
                .build());
    }
}
