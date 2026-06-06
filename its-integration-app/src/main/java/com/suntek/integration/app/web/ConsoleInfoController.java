/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.app.web;

import com.suntek.integration.api.admin.ConsoleInfoResponse;
import com.suntek.integration.app.security.IntegrationSecurityProperties;
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
