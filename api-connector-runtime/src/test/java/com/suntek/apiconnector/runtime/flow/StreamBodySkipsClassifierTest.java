/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.runtime.compile.PlanCompiler;
import com.suntek.apiconnector.runtime.transport.FakeTransport;
import com.suntek.apiconnector.transport.RawHttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.channels.Channels;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StreamBodySkipsClassifierTest {

    @Test
    void streamBodyDoesNotEvaluateStatusConditions() {
        String yaml = """
                schema:
                  version: 1
                definition:
                  id: stream-a
                  revision: 1
                  authProfile: none
                credentials:
                  apiKey:
                    type: secret
                    valueRef: secret/stream/api-key
                    apiId: stream-a
                variables:
                  baseUrl:
                    type: string
                    scope: GLOBAL
                    value: "https://stream.example"
                limits:
                  maxAuthAttempts: 0
                  maxAuthDepth: 0
                  transitionLimit: 8
                  executionTimeout: 10s
                requests:
                  download:
                    method: GET
                    url: "{global.baseUrl}/file"
                flows:
                  business:
                    steps:
                      - id: call
                        request: download
                        transitions:
                          - when: { status: 403 }
                            action: FAIL
                          - when: { status: 200 }
                            action: SUCCESS
                """;
        FakeTransport transport = new FakeTransport().enqueue(new RawHttpResponse(
                403,
                Map.of(),
                new ResponseBody.StreamBody(Channels.newChannel(java.io.InputStream.nullInputStream()), Optional.empty()),
                true
        ));
        var result = LinearFlowExecutor.execute(PlanCompiler.compile(yaml), transport);
        assertThat(result.outcome()).isEqualTo(StepOutcomeType.SUCCESS);
        assertThat(result.httpStatus()).contains(403);
        assertThat(transport.invocations()).hasSize(1);
    }
}
