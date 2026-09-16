/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

import com.suntek.apiconnector.core.value.DataValue;

import java.util.Map;
import java.util.Optional;

/**
 * Host command to start an execution.
 *
 * @param apiId    definition id
 * @param flowId   flow to run; default {@code business}
 * @param input    EXECUTION-scoped values only
 * @param revision published revision, empty means current published
 * @param options  timeouts and replay flags
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecuteCommand(
        String apiId,
        String flowId,
        Map<String, DataValue> input,
        Optional<String> revision,
        ExecuteOptions options
) {
}
