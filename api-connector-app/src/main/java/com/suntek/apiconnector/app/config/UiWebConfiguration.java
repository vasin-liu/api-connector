/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 托管 {@code api-connector-ui} 静态资源，与 API 共用 {@code server.port}。
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
@Configuration
public class UiWebConfiguration implements WebMvcConfigurer {

    private static final String CONSOLE_INDEX = "forward:/console/index.html";

    /**
     * 根路径重定向到控制台。
     *
     * @param registry 视图注册器
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/console/");
        registry.addViewController("/console").setViewName(CONSOLE_INDEX);
        registry.addViewController("/console/").setViewName(CONSOLE_INDEX);
    }

    /**
     * 注册控制台静态资源；无匹配文件时回退到 SPA {@code index.html}。
     *
     * @param registry 资源处理器注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/console/**")
                .addResourceLocations("classpath:/static/console/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = super.getResource(resourcePath, location);
                        if (requested != null) {
                            return requested;
                        }
                        // 客户端路由（/console/connectors 等）回退 index.html
                        return super.getResource("index.html", location);
                    }
                });
    }
}
