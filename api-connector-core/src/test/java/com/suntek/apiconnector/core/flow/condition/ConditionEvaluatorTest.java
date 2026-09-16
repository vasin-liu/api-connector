/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.value.DataValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oracle: {@code docs/design/v2.7-greenfield/05-condition-transition-tables.md} tables J2, J3, J4, H.
 */
class ConditionEvaluatorTest {

    private static final List<TransitionRule> MOCK_C_GET_DATA = List.of(
            new TransitionRule(
                    Condition.AllCondition.of(
                            Condition.StatusCondition.exact(403),
                            Condition.HeaderCondition.exists("X-Challenge")
                    ),
                    TransitionAction.AUTHENTICATE
            ),
            new TransitionRule(
                    Condition.AllCondition.of(
                            Condition.StatusCondition.exact(403),
                            Condition.JsonPathCondition.equalsString("$.error", "PERMISSION_DENIED")
                    ),
                    TransitionAction.FAIL
            ),
            new TransitionRule(Condition.StatusCondition.exact(200), TransitionAction.SUCCESS),
            new TransitionRule(Condition.StatusCondition.exact(503), TransitionAction.RETRY_REQUEST)
    );

    private static final List<TransitionRule> MOCK_B_GET_DATA = List.of(
            new TransitionRule(
                    Condition.AllCondition.of(
                            Condition.StatusCondition.exact(401),
                            Condition.JsonPathCondition.equalsString("$.error", "UNAUTHORIZED")
                    ),
                    TransitionAction.AUTHENTICATE
            ),
            new TransitionRule(Condition.StatusCondition.exact(200), TransitionAction.SUCCESS)
    );

    @Test
    void j2C2_challengeHeaderBeatsBare403() {
        ConditionContext ctx = completed(403, Map.of("X-Challenge", List.of("abc")), "{}");
        assertThat(TransitionEvaluator.firstMatch(ctx, MOCK_C_GET_DATA))
                .contains(new TransitionEvaluator.MatchedTransition(0, TransitionAction.AUTHENTICATE));
    }

    @Test
    void j2C4_permissionJsonDoesNotAuthenticate() {
        ConditionContext ctx = completed(403, Map.of(), "{\"error\":\"PERMISSION_DENIED\"}");
        assertThat(TransitionEvaluator.firstMatch(ctx, MOCK_C_GET_DATA))
                .contains(new TransitionEvaluator.MatchedTransition(1, TransitionAction.FAIL));
    }

    @Test
    void j2C8_headerNameIsCaseInsensitive() {
        ConditionContext ctx = completed(403, Map.of("x-challenge", List.of("abc")), "{}");
        assertThat(TransitionEvaluator.firstMatch(ctx, MOCK_C_GET_DATA))
                .contains(new TransitionEvaluator.MatchedTransition(0, TransitionAction.AUTHENTICATE));
    }

    @Test
    void j3B5_jsonpathAloneDoesNotTriggerAuth() {
        ConditionContext ctx = completed(403, Map.of(), "{\"error\":\"UNAUTHORIZED\"}");
        assertThat(TransitionEvaluator.firstMatch(ctx, MOCK_B_GET_DATA)).isEmpty();
    }

    @Test
    void j4_allAnyNot() {
        ConditionContext ctx = completed(
                403,
                Map.of("X-Challenge", List.of("n1")),
                "{\"error\":\"TOKEN_EXPIRED\"}"
        );
        assertThat(ConditionEvaluator.matches(
                Condition.AllCondition.of(
                        Condition.StatusCondition.exact(403),
                        Condition.HeaderCondition.exists("X-Challenge")
                ),
                ctx
        )).isTrue();
        assertThat(ConditionEvaluator.matches(
                Condition.AllCondition.of(
                        Condition.StatusCondition.exact(403),
                        Condition.JsonPathCondition.equalsString("$.error", "PERMISSION_DENIED")
                ),
                ctx
        )).isFalse();
        assertThat(ConditionEvaluator.matches(
                Condition.AnyCondition.of(
                        Condition.StatusCondition.exact(200),
                        Condition.StatusCondition.exact(403)
                ),
                ctx
        )).isTrue();
        assertThat(ConditionEvaluator.matches(
                Condition.NotCondition.of(Condition.StatusCondition.exact(200)),
                ctx
        )).isTrue();
        assertThat(ConditionEvaluator.matches(
                Condition.AllCondition.of(
                        Condition.StatusCondition.exact(403),
                        Condition.NotCondition.of(Condition.HeaderCondition.exists("X-Challenge"))
                ),
                ctx
        )).isFalse();
    }

    @Test
    void h3_statusAndHeaderFalseWithoutResponse_variableStillEvaluates() {
        VariableLookup vars = (scope, name) -> {
            if (scope == VariableScope.EXECUTION && "ready".equals(name)) {
                return Optional.of(new DataValue.BooleanValue(true));
            }
            return Optional.empty();
        };
        ConditionContext ctx = new ConditionContext(
                java.util.OptionalInt.empty(),
                Map.of(),
                Optional.empty(),
                vars
        );
        assertThat(ConditionEvaluator.matches(Condition.StatusCondition.exact(200), ctx)).isFalse();
        assertThat(ConditionEvaluator.matches(Condition.HeaderCondition.exists("X-Challenge"), ctx)).isFalse();
        assertThat(ConditionEvaluator.matches(
                Condition.VariableCondition.exists(VariableScope.EXECUTION, "ready"),
                ctx
        )).isTrue();
        assertThat(ConditionEvaluator.matches(
                Condition.VariableCondition.exists(VariableScope.EXECUTION, "missing"),
                ctx
        )).isFalse();
    }

    @Test
    void headerEqualsIsCaseSensitive() {
        ConditionContext ctx = completed(200, Map.of("X-Mode", List.of("Strict")), "{}");
        assertThat(ConditionEvaluator.matches(Condition.HeaderCondition.equalsValue("x-mode", "Strict"), ctx))
                .isTrue();
        assertThat(ConditionEvaluator.matches(Condition.HeaderCondition.equalsValue("X-Mode", "strict"), ctx))
                .isFalse();
    }

    @Test
    void emptyHeaderValueIsNotExists() {
        ConditionContext ctx = completed(403, Map.of("X-Challenge", List.of("")), "{}");
        assertThat(ConditionEvaluator.matches(Condition.HeaderCondition.exists("X-Challenge"), ctx)).isFalse();
    }

    private static ConditionContext completed(int status, Map<String, List<String>> headers, String json) {
        return ConditionContext.completed(
                status,
                headers,
                new ResponseBody.BytesBody(json.getBytes(StandardCharsets.UTF_8), Optional.of("application/json")),
                VariableLookup.empty()
        );
    }
}
