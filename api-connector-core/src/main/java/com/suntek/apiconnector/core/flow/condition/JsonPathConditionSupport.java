/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.jsonpath.ForbiddenJsonPathException;
import com.suntek.apiconnector.core.jsonpath.RestrictedJsonPath;
import com.suntek.apiconnector.core.validate.ValidationCodes;

import java.util.Optional;

/**
 * Maps JSONPath profile violations onto DefinitionValidator codes.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class JsonPathConditionSupport {

    private JsonPathConditionSupport() {
    }

    /**
     * @param path JSONPath from YAML
     * @return {@code VAL_JSONPATH_FORBIDDEN} when the path uses Filter/{@code ..}/calls
     */
    public static Optional<String> forbiddenCode(String path) {
        try {
            RestrictedJsonPath.validate(path);
            return Optional.empty();
        } catch (ForbiddenJsonPathException ex) {
            return Optional.of(ValidationCodes.VAL_JSONPATH_FORBIDDEN);
        }
    }
}
