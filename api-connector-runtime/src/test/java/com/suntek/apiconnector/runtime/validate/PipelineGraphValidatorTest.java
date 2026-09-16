/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.validate;

import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineGraphValidatorTest {

    @Test
    void p0_mockCCompiles() {
        var plan = PlanCompiler.compile(mockC());
        assertThat(plan.capabilities()).contains(PlanCapability.PIPELINE_GRAPH);
        assertThat(plan.pipelines()).containsKeys("jsonBody", "challengeHmac");
    }

    @Test
    void p4_cycleFailsCompile() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: canonical.out\n        to: hmac.in\n      - from: hmac.out\n        to: canonical.in_secret"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_CYCLE);
    }

    @Test
    void p2_stringToBytesWithoutCodec() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: hmac.hex\n        to: hmac.in"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p3_missingRequiredEdge() {
        String yaml = mockC().replace(
                "    edges:\n      - from: canonical.out\n        to: hmac.in\n      - from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC\n",
                "    edges:\n      - from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC\n"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_PORT_REQUIRED);
    }

    @Test
    void p5_hmacKeyFromStringNonce() {
        String yaml = mockC().replace(
                "- from:\n          secretRef: apiKey\n        to: hmac.key\n        sink: HMAC",
                "- from:\n          var: flow.nonce\n        to: hmac.key\n        sink: HMAC"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p1_crossPipelineEdgeIllegal() {
        String yaml = mockC().replace(
                "- from: canonical.out\n        to: hmac.in",
                "- from: json.out\n        to: hmac.in"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p6_hmacStringOutputCannotBeHttpBody() {
        String yaml = mockC().replace(
                "    body:\n      pipeline: jsonBody",
                "    body:\n      pipeline: challengeHmac"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    @Test
    void p7_codecThenHmacWithoutEdgesFailsType() {
        String yaml = mockC().replace(
                "          out: { name: body, type: bytes }\n    edges: []",
                "          out: { name: body, type: bytes }\n      - id: danglingHmac\n        type: signer.hmac-sha256\n        ports:\n          in: { name: canonical, type: bytes, required: true }\n          key: { name: key, type: secret, required: true }\n          out: { name: hex, type: string }\n    edges: []"
        );
        assertCode(yaml, ValidationCodes.VAL_PIPE_TYPE);
    }

    private static void assertCode(String yaml, String code) {
        assertThatThrownBy(() -> PlanCompiler.compile(yaml))
                .isInstanceOf(DefinitionValidationException.class)
                .satisfies(ex -> assertThat(((DefinitionValidationException) ex).violations().stream()
                        .map(v -> v.code())
                        .collect(Collectors.toSet())).contains(code));
    }

    private static String mockC() {
        try (InputStream in = PipelineGraphValidatorTest.class.getResourceAsStream("/definitions/mock-c.yaml")) {
            if (in == null) {
                throw new IllegalStateException("missing mock-c.yaml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
