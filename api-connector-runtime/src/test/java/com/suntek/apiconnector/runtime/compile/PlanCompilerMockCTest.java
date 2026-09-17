/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.runtime.plan.CompiledStep;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCompilerMockCTest {

    @Test
    void mockCFirstTransitionIsAuthenticate() {
        ExecutionPlan plan = PlanCompiler.compile(mockC());
        CompiledStep getData = plan.business().steps().stream()
                .filter(step -> "getData".equals(step.stepId()))
                .findFirst()
                .orElseThrow();
        assertThat(getData.transitions().getFirst().action()).isEqualTo(TransitionAction.AUTHENTICATE);
        assertThat(getData.transitions().getFirst().thenAction()).contains(TransitionAction.REPLAY_REQUEST);
        assertThat(plan.capabilities()).contains(
                PlanCapability.LINEAR_FLOW,
                PlanCapability.AUTH_FLOW,
                PlanCapability.REPLAY,
                PlanCapability.PIPELINE_GRAPH
        );
    }

    @Test
    void mockCCapabilitiesAreNotLinearOnly() {
        ExecutionPlan plan = PlanCompiler.compile(mockC());
        assertThat(plan.capabilities()).isNotEqualTo(Set.of(PlanCapability.LINEAR_FLOW));
    }

    private static String mockC() {
        try (InputStream in = PlanCompilerMockCTest.class.getResourceAsStream("/definitions/mock-c.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-c.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
