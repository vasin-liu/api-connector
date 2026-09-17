/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.state;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.runtime.plan.CompiledVariable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultVariableRuntimeTest {

    @Test
    void globalWriteIsRejectedAtRuntime() {
        DefaultVariableRuntime runtime = new DefaultVariableRuntime(Map.of(
                "baseUrl",
                new CompiledVariable(
                        "baseUrl",
                        VariableScope.GLOBAL,
                        "string",
                        Optional.of(new DataValue.StringValue("https://a.example"))
                )
        ));
        VariableRuntime.StateMutation mutation = runtime.beginLocal();
        assertThatThrownBy(() -> mutation.set(VariableScope.GLOBAL, "baseUrl", new DataValue.StringValue("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAL_GLOBAL_WRITE");
        assertThat(runtime.get(VariableScope.GLOBAL, "baseUrl"))
                .contains(new DataValue.StringValue("https://a.example"));
    }

    @Test
    void failureDiscardsLocalMutation() {
        DefaultVariableRuntime runtime = new DefaultVariableRuntime(Map.of());
        VariableRuntime.StateMutation mutation = runtime.beginLocal();
        mutation.set(VariableScope.EXECUTION, "token", new DataValue.StringValue("secret"));
        runtime.discard(mutation);
        assertThat(runtime.get(VariableScope.EXECUTION, "token")).isEmpty();
    }

    @Test
    void successCommitsLocalMutation() {
        DefaultVariableRuntime runtime = new DefaultVariableRuntime(Map.of());
        VariableRuntime.StateMutation mutation = runtime.beginLocal();
        mutation.set(VariableScope.EXECUTION, "token", new DataValue.StringValue("ok"));
        runtime.commit(mutation, StepOutcomeType.SUCCESS);
        assertThat(runtime.get(VariableScope.EXECUTION, "token")).contains(new DataValue.StringValue("ok"));
    }
}
