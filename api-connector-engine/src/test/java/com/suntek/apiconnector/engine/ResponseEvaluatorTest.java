package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.spec.model.ResponseSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseEvaluatorTest {

    @Test
    void extractsIdpsStyleFields() {
        String body = """
                {"success":true,"msg":"ok","obj":{"roads":[]},"code":0}
                """;
        ResponseSpec spec = new ResponseSpec("$.success == true", "$.obj", "$.msg", "$.code");
        ResponseEvaluation evaluation = new ResponseEvaluator().evaluate(spec, body);

        assertTrue(evaluation.success());
        assertEquals("ok", evaluation.vendorMessage());
        assertEquals("0", evaluation.vendorCode());
        assertInstanceOf(java.util.Map.class, evaluation.parsedData());
    }

    @Test
    void failsWhenSuccessRuleNotMet() {
        ResponseSpec spec = new ResponseSpec("$.success == true", "$.obj", null, null);
        ResponseEvaluation evaluation = new ResponseEvaluator().evaluate(spec, "{\"success\":false}");
        assertFalse(evaluation.success());
    }
}
