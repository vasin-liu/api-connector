/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ProtocolDefinitions {

    private ProtocolDefinitions() {
    }

    static String wenxin() {
        return read("wenxin.yaml");
    }

    static String gaodeTraffic() {
        return read("gaode-traffic.yaml");
    }

    static String idpsAksk() {
        return read("idps-aksk.yaml");
    }

    static String huaweiIvs() {
        return read("huawei-ivs.yaml");
    }

    private static String read(String name) {
        Path[] candidates = {
                Path.of("..", "docs", "design", "v2.7-protocols", name),
                Path.of("docs", "design", "v2.7-protocols", name)
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
                } catch (IOException e) {
                    throw new IllegalStateException("cannot read " + path, e);
                }
            }
        }
        throw new IllegalStateException("missing protocol YAML " + name);
    }
}
