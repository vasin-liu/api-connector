/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI 分组、标签与可读性约定。
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI integrationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ITS Integration Platform")
                        .version("1.0.0")
                        .description("""
                                独立第三方 HTTP 对接平台 API。

                                **厂家 API 怎么读**
                                1. 右上角分组选 `vendor-{code3rd}`（如 `vendor-IDPS`）— 只看该厂家
                                2. 左侧标签按厂家 + 业务分组（如 `IDPS · 路况感知 · 道路`）
                                3. 每个厂家 API 对应一条独立 invoke 操作，可直接 Try it out

                                **平台 API**
                                - `runtime` — 通用 invoke 模板（高级）
                                - `admin` — 配置管理

                                认证由 Connector Spec + Auth Profile 在平台侧完成。
                                """)
                        .contact(new Contact().name("ITS Integration").url("https://pcitech.com")))
                .addTagsItem(new Tag().name("Runtime · Invoke").description("平台级 invoke 模板（高级）"))
                .addTagsItem(new Tag().name("Admin · Connectors").description("连接器配置 CRUD / 发布"))
                .addTagsItem(new Tag().name("Admin · Profiles").description("Auth Profile 元数据（控制台表单）"))
                .addTagsItem(new Tag().name("Admin · Operations").description("运维操作（如同步注册表）"));
    }

    @Bean
    public GroupedOpenApi runtimeOpenApi() {
        return GroupedOpenApi.builder()
                .group("runtime")
                .displayName("Runtime — 平台 invoke 模板")
                .pathsToMatch("/api/v1/integrations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminOpenApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin — 配置管理")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }

    @Bean
    public OperationCustomizer adminOperationsTagging() {
        return (operation, handlerMethod) -> {
            if (!(handlerMethod instanceof HandlerMethod hm)) {
                return operation;
            }
            if (hm.getBeanType().getSimpleName().contains("IntegrationSyncController")) {
                operation.addTagsItem("Admin · Operations");
                if (operation.getSummary() == null || operation.getSummary().isBlank()) {
                    operation.setSummary("从本地库刷新已发布连接器到运行时注册表");
                }
                if (operation.getOperationId() == null || operation.getOperationId().isBlank()) {
                    operation.setOperationId("reloadConnectorsFromStore");
                }
            }
            return operation;
        };
    }
}
