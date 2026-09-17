/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.flow.condition.Condition;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.CompiledStep;
import com.suntek.apiconnector.runtime.plan.CompiledTransition;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.plan.Replayability;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.plan.StepKind;
import com.suntek.apiconnector.runtime.plan.UrlTemplate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCompilerMockATest {

    @Test
    void twoCompilesOfMockAProduceIdenticalPlanId() {
        String yaml = mockA();
        ExecutionPlan first = PlanCompiler.compile(yaml);
        ExecutionPlan second = PlanCompiler.compile(yaml);
        assertThat(first.planId()).isEqualTo(second.planId());
        assertThat(first.planId()).matches("[0-9a-f]{64}");
    }

    @Test
    void mockAMatchesSectionSSnapshot() {
        ExecutionPlan plan = PlanCompiler.compile(mockA());
        assertThat(plan.definitionId()).isEqualTo("mock-a");
        assertThat(plan.definitionRevision()).isEqualTo("1");
        assertThat(plan.authProfile()).isEqualTo("api-key-query");
        assertThat(plan.credentialRef()).isEqualTo("apiKey");
        assertThat(plan.capabilities()).isEqualTo(Set.of(PlanCapability.LINEAR_FLOW));
        assertThat(plan.authentication()).isEmpty();
        assertThat(plan.limits().maxAuthAttempts()).isZero();

        CompiledRequest getStatus = plan.requests().get("getStatus");
        assertThat(getStatus.method()).isEqualTo("GET");
        assertThat(getStatus.url().parts()).containsExactly(
                new UrlTemplate.UrlPart.VarRef(VariableScope.GLOBAL, "baseUrl"),
                new UrlTemplate.UrlPart.Literal("/v1/status")
        );
        assertThat(getStatus.query()).hasSize(1);
        assertThat(getStatus.query().getFirst().name()).isEqualTo("key");
        assertThat(getStatus.query().getFirst().secretRef()).contains("apiKey");
        assertThat(getStatus.query().getFirst().sink()).contains(SecretSink.AUTHORIZATION);
        assertThat(getStatus.replay().replayability()).isEqualTo(Replayability.SAFE);
        assertThat(getStatus.replay().allowAutomaticReplay()).isFalse();
        assertThat(getStatus.replay().maxAttempts()).isEqualTo(1);

        CompiledStep call = plan.business().steps().getFirst();
        assertThat(call.stepId()).isEqualTo("call");
        assertThat(call.kind()).isEqualTo(StepKind.REQUEST);
        assertThat(call.requestId()).contains("getStatus");
        assertThat(call.pipelineId()).contains("identity");

        CompiledTransition t0 = call.transitions().get(0);
        assertThat(t0.transitionId()).isEqualTo("t0");
        assertThat(t0.condition()).isEqualTo(Condition.StatusCondition.exact(200));
        assertThat(t0.action()).isEqualTo(TransitionAction.SUCCESS);

        CompiledTransition t1 = call.transitions().get(1);
        assertThat(t1.transitionId()).isEqualTo("t1");
        assertThat(t1.condition()).isEqualTo(Condition.StatusCondition.range(500, 599));
        assertThat(t1.action()).isEqualTo(TransitionAction.FAIL);
        assertThat(t1.outcomeOverride()).contains(StepOutcomeType.RETRYABLE_FAILURE);
    }

    private static String mockA() {
        try (InputStream in = PlanCompilerMockATest.class.getResourceAsStream("/definitions/mock-a.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-a.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
