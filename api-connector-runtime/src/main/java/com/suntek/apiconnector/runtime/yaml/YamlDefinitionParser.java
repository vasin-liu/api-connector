/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.yaml;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Map;
import java.util.Objects;

/**
 * Parses Canonical API Definition YAML into a map graph. Does not validate semantics.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class YamlDefinitionParser {

    private YamlDefinitionParser() {
    }

    /**
     * @param yaml Canonical Definition YAML
     * @return root mapping
     */
    public static Map<String, Object> parse(String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        LoaderOptions options = new LoaderOptions();
        Yaml loader = new Yaml(new SafeConstructor(options));
        Object loaded = loader.load(yaml);
        if (loaded == null) {
            throw new IllegalArgumentException("YAML document is empty");
        }
        return YamlMaps.map(loaded);
    }
}
