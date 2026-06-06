/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.spec;

import com.suntek.integration.spec.model.ConnectorSpec;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * 从 YAML 加载 {@link ConnectorSpec}。
 */
public final class ConnectorSpecLoader {

    private final Yaml yaml = new Yaml();

    /**
     * 解析 YAML 输入流为连接器规格。
     *
     * @param in YAML 输入流
     * @return 连接器规格
     */
    @SuppressWarnings("unchecked")
    public ConnectorSpec load(InputStream in) {
        Map<String, Object> root = yaml.load(in);
        return ConnectorSpecParser.parse(root);
    }
}
