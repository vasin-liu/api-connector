/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.app.config;

import com.suntek.apiconnector.connectors.BuiltinConnectorCatalogs;
import com.suntek.apiconnector.engine.ConnectorRegistry;
import com.suntek.apiconnector.spec.ConnectorSpecLoader;
import com.suntek.apiconnector.spec.model.ConnectorSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 启动时注册内置连接器：优先 Java Catalog，YAML 仅作未覆盖 code3rd 的补充。
 */
@Configuration
public class ConnectorBootstrapConfiguration {

    @Bean
    public Object loadClasspathConnectors(ConnectorRegistry registry) throws Exception {
        Set<String> registered = new HashSet<>();
        for (ConnectorSpec spec : BuiltinConnectorCatalogs.loadAll()) {
            register(registry, spec);
            registered.add(spec.code3rd());
        }
        ConnectorSpecLoader loader = new ConnectorSpecLoader();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolveOptionalYamlConnectors(resolver);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith("template-")) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                ConnectorSpec spec = loader.load(in);
                if (registered.contains(spec.code3rd())) {
                    continue;
                }
                register(registry, spec);
            }
        }
        return new Object();
    }

    private static Resource[] resolveOptionalYamlConnectors(PathMatchingResourcePatternResolver resolver) {
        try {
            Resource[] resources = resolver.getResources("classpath:connectors/*.yaml");
            if (resources.length == 0) {
                return resources;
            }
            java.util.List<Resource> existing = new java.util.ArrayList<>();
            for (Resource resource : resources) {
                if (resource.exists()) {
                    existing.add(resource);
                }
            }
            return existing.toArray(Resource[]::new);
        } catch (Exception ex) {
            return new Resource[0];
        }
    }

    private static void register(ConnectorRegistry registry, ConnectorSpec spec) {
        ConnectorSpec effective = applyBaseUrlOverride(spec);
        Map<String, String> creds = new HashMap<>();
        creds.put("appId", System.getenv().getOrDefault(effective.code3rd() + "_APP_ID", ""));
        creds.put("appSecret", System.getenv().getOrDefault(effective.code3rd() + "_APP_SECRET", ""));
        creds.put("publicKey", System.getenv().getOrDefault(effective.code3rd() + "_PUBLIC_KEY", ""));
        registry.register(effective, creds);
    }

    private static ConnectorSpec applyBaseUrlOverride(ConnectorSpec spec) {
        String override = System.getenv(spec.code3rd() + "_BASE_URL");
        if (override == null || override.isBlank()) {
            return spec;
        }
        return new ConnectorSpec(
                spec.code3rd(),
                spec.version(),
                override.trim(),
                spec.protocol(),
                spec.auth(),
                spec.endpoints(),
                spec.response(),
                spec.transport(),
                spec.mapping(),
                spec.transform());
    }
}
