/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.api;

import com.suntek.apiconnector.core.value.DataValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApiClientTypesTest {

    @Test
    void executeCommandDoesNotReferenceLegacyEnginePackages() {
        ExecuteCommand command = new ExecuteCommand(
                "mock-a",
                "business",
                Map.of("k", new DataValue.StringValue("v")),
                Optional.empty(),
                new ExecuteOptions(Duration.ofSeconds(10), false, Map.of()));
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                "e1", "mock-a", "1", "plan", Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(command.apiId()).isEqualTo("mock-a");
        assertThat(snapshot.planId()).isEqualTo("plan");
        assertThat(ApiClient.class.getPackageName()).startsWith("com.suntek.apiconnector.core");
    }
}
