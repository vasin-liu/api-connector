/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.client;

import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretValue;

import java.util.Locale;
import java.util.Map;

/**
 * Rejects execute input that targets SESSION/GLOBAL or carries secret literals.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class ExecuteInputValidator {

    private ExecuteInputValidator() {
    }

    /**
     * @param command host command
     */
    public static void validate(ExecuteCommand command) {
        Map<String, DataValue> input = command.input();
        if (input == null || input.isEmpty()) {
            return;
        }
        input.forEach((key, value) -> {
            if (targetsForbiddenScope(key)) {
                throw new ExecuteException(
                        ValidationCodes.VAL_INPUT_SCOPE,
                        "execute input cannot target SESSION or GLOBAL: " + key
                );
            }
            if (value instanceof SecretValue) {
                throw new ExecuteException(
                        ValidationCodes.VAL_SECRET_LITERAL,
                        "secret literals are not allowed in execute input"
                );
            }
        });
    }

    private static boolean targetsForbiddenScope(String key) {
        int dot = key.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String scope = key.substring(0, dot).toUpperCase(Locale.ROOT);
        return scope.equals(VariableScope.SESSION.name()) || scope.equals(VariableScope.GLOBAL.name());
    }
}
