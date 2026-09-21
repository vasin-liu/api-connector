/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.validate;

import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.core.validate.Violation;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionValidatorTest {

    @Test
    void missingStepId() {
        assertCode(definition("""
                - request: ping
                  transitions:
                    - when: { status: 200 }
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_STEP_ID_MISSING);
    }

    @Test
    void globalWrite() {
        assertCode(definition("""
                - id: write
                  assign:
                    global.baseUrl: "https://evil.example"
                - id: call
                  request: ping
                  transitions:
                    - when: { status: 200 }
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_GLOBAL_WRITE);
    }

    @Test
    void authenticateWithoutFlow() {
        assertCode(definition("""
                - id: call
                  request: ping
                  transitions:
                    - when: { status: 401 }
                      action: AUTHENTICATE
                      then: REPLAY_REQUEST
                    - when: { status: 200 }
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_AUTH_FLOW_REQUIRED);
    }

    @Test
    void authenticateWithoutThen() {
        assertCode(definition("""
                - id: call
                  request: ping
                  transitions:
                    - when: { status: 401 }
                      action: AUTHENTICATE
                    - when: { status: 200 }
                      action: SUCCESS
                """, """
                authentication:
                  steps:
                    - id: login
                      assign:
                        execution.ready: true
                """, 2),
                ValidationCodes.VAL_THEN_MISSING);
    }

    @Test
    void unknownNowForm() {
        assertCode(definition("""
                - id: stamp
                  assign:
                    execution.ts: { now: epochSeconds }
                - id: call
                  request: ping
                  transitions:
                    - when: { status: 200 }
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_ASSIGN_FORM);
    }

    @Test
    void emptyAll() {
        assertCode(definition("""
                - id: call
                  request: ping
                  transitions:
                    - when:
                        all: []
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_EMPTY_ALL);
    }

    @Test
    void emptyAny() {
        assertCode(definition("""
                - id: call
                  request: ping
                  transitions:
                    - when:
                        any: []
                      action: SUCCESS
                """, emptyAuth(), 0),
                ValidationCodes.VAL_EMPTY_ANY);
    }

    private static void assertCode(String yaml, String code) {
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations())
                        .extracting(Violation::code)
                        .contains(code));
    }

    private static String emptyAuth() {
        return """
                authentication:
                  steps: []
                """;
    }

    private static String definition(String steps, String authentication, int maxAuthAttempts) {
        return """
                schema:
                  version: 1
                definition:
                  id: val-fragment
                  revision: 1
                  authProfile: none
                credentials:
                  apiKey:
                    type: secret
                    valueRef: secret/val/api-key
                    apiId: val-fragment
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://val.example"
                limits:
                  maxAuthAttempts: %d
                  maxAuthDepth: 1
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  ping:
                    method: GET
                    url: "{global.baseUrl}/ping"
                flows:
                  business:
                    steps:
                %s
                %s
                """.formatted(
                maxAuthAttempts,
                indent(steps, 10),
                indent(authentication, 2)
        );
    }

    private static String indent(String block, int spaces) {
        String pad = " ".repeat(spaces);
        return block.strip().lines().map(line -> pad + line).collect(Collectors.joining("\n"));
    }
}
