/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.api.admin;

import com.suntek.apiconnector.api.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 管理 API 参数与业务校验异常 → 4xx JSON。
 */
@RestControllerAdvice(basePackageClasses = IntegrationAdminController.class)
public class AdminApiExceptionHandler {

    /**
     * 非法参数或业务规则违反。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.builder()
                .code("BAD_REQUEST")
                .message(ex.getMessage())
                .build());
    }
}
