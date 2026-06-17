/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.app.web;

import com.suntek.apiconnector.api.admin.ConsoleInfoResponse;
import com.suntek.apiconnector.app.security.IntegrationSecurityProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台 UI 运行时信息。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class ConsoleInfoController {

    private final IntegrationSecurityProperties securityProperties;

    public ConsoleInfoController(IntegrationSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @GetMapping("/console-info")
    public ConsoleInfoResponse consoleInfo() {
        return new ConsoleInfoResponse(securityProperties.isEnabled(), securityProperties.getApiKeyHeader());
    }
}
