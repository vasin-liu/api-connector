/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ITS 第三方通用对接平台启动类。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@SpringBootApplication(scanBasePackages = "com.suntek.apiconnector")
public class IntegrationApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
