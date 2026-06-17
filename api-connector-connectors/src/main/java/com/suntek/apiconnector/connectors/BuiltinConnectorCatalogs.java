/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.connectors;

import com.suntek.apiconnector.spec.model.ConnectorSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 内置 Catalog 注册表：启动时加载，作为受管连接器的唯一 Spec 来源。
 */
public final class BuiltinConnectorCatalogs {

    private static final Map<String, String> CODE_ALIASES = Map.of("BaiduGpt", "BAIDU_WENXIN");

    private static final Map<String, ConnectorSpec> BY_CODE = loadIndex();

    private BuiltinConnectorCatalogs() {
    }

    public static Set<String> managedCode3rds() {
        return Collections.unmodifiableSet(BY_CODE.keySet());
    }

    public static boolean isManaged(String code3rd) {
        return code3rd != null && BY_CODE.containsKey(code3rd);
    }

    public static String canonicalCode3rd(String code3rd) {
        if (code3rd == null) {
            return null;
        }
        return CODE_ALIASES.getOrDefault(code3rd, code3rd);
    }

    public static ConnectorSpec catalogSpec(String code3rd) {
        String canonical = canonicalCode3rd(code3rd);
        ConnectorSpec spec = BY_CODE.get(canonical);
        if (spec == null) {
            spec = BY_CODE.get(code3rd);
        }
        if (spec == null) {
            throw new IllegalArgumentException("Not a catalog-managed connector: " + code3rd);
        }
        if (code3rd != null && !code3rd.equals(spec.code3rd())) {
            return rebindCode(spec, code3rd);
        }
        return spec;
    }

    public static java.util.List<ConnectorSpec> loadAll() {
        return BY_CODE.values().stream().toList();
    }

    private static Map<String, ConnectorSpec> loadIndex() {
        Map<String, ConnectorSpec> map = new LinkedHashMap<>();
        for (ConnectorSpec spec : scanAll()) {
            map.put(spec.code3rd(), spec);
            for (Map.Entry<String, String> alias : CODE_ALIASES.entrySet()) {
                if (alias.getValue().equals(spec.code3rd())) {
                    map.put(alias.getKey(), rebindCode(spec, alias.getKey()));
                }
            }
        }
        return Map.copyOf(map);
    }

    private static ConnectorSpec rebindCode(ConnectorSpec spec, String code3rd) {
        return new ConnectorSpec(
                code3rd,
                spec.version(),
                spec.baseUrl(),
                spec.protocol(),
                spec.auth(),
                spec.endpoints(),
                spec.response(),
                spec.transport(),
                spec.transform());
    }

    private static java.util.List<ConnectorSpec> scanAll() {
        return java.util.List.of(
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.demo.DemoNoneConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.demo.DemoAkskConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.idps.IdpsConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.gaode.GaodeOpenPlatformConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.gaode.GaodeTrafficConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.baidu.BaiduMapConnectorCatalog.class),
                com.suntek.apiconnector.spec.catalog.CatalogConnectorScanner.scan(
                        com.suntek.apiconnector.connectors.baidu.BaiduWenxinConnectorCatalog.class));
    }
}
