/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.http.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inputs for Condition evaluation: last transport response plus committed variables.
 *
 * @param httpStatus HTTP status when a completed response exists
 * @param headers    response headers (names matched case-insensitively)
 * @param body       response body when present
 * @param variables  committed VariableRuntime
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ConditionContext(
        OptionalInt httpStatus,
        Map<String, List<String>> headers,
        Optional<ResponseBody> body,
        VariableLookup variables
) {

    /**
     * @param httpStatus status or empty
     * @param headers    headers
     * @param body       body
     * @param variables  variables
     */
    public ConditionContext {
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        body = Objects.requireNonNull(body, "body");
        variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * @return context with no response (status/header/jsonpath evaluate false)
     */
    public static ConditionContext noResponse() {
        return new ConditionContext(OptionalInt.empty(), Map.of(), Optional.empty(), VariableLookup.empty());
    }

    /**
     * @param status    HTTP status
     * @param headers   headers
     * @param body      body
     * @param variables variables
     * @return context
     */
    public static ConditionContext completed(
            int status,
            Map<String, List<String>> headers,
            ResponseBody body,
            VariableLookup variables
    ) {
        return new ConditionContext(
                OptionalInt.of(status),
                headers,
                Optional.of(body),
                variables
        );
    }
}
