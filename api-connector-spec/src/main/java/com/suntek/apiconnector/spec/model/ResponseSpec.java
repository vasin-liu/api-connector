/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.spec.model;

/**
 * Vendor response mapping rules.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ResponseSpec {

    private final String successWhen;
    private final String dataPath;
    private final String messagePath;
    private final String codePath;

    public ResponseSpec(String successWhen, String dataPath, String messagePath, String codePath) {
        this.successWhen = successWhen;
        this.dataPath = dataPath;
        this.messagePath = messagePath;
        this.codePath = codePath;
    }

    public String successWhen() {
        return successWhen;
    }

    public String dataPath() {
        return dataPath;
    }

    public String messagePath() {
        return messagePath;
    }

    public String codePath() {
        return codePath;
    }
}
