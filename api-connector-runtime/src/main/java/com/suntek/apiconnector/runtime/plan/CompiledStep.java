/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import com.suntek.apiconnector.core.flow.StepOutcomeType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compiled flow step.
 *
 * @param stepId         step id
 * @param kind           kind
 * @param requestId      request id when REQUEST
 * @param pipelineId     pipeline id when REQUEST/PIPELINE
 * @param assigns        assign payload (raw until 3.1)
 * @param extract        extract payload
 * @param transitions    first-match list
 * @param extraCommitOn  extra commit outcomes (e.g. CHALLENGE)
 * @param sessionCommit  onCommit session mutation
 * @param pipelineOutput pipeline port bindings (flow.var → node.port)
 * @param stepInput      optional request/pipeline input map
 * @author Gensokyo
 * @since 2026-09-14
 */
public record CompiledStep(
        String stepId,
        StepKind kind,
        Optional<String> requestId,
        Optional<String> pipelineId,
        List<Map<String, Object>> assigns,
        Optional<Map<String, Object>> extract,
        List<CompiledTransition> transitions,
        EnumSet<StepOutcomeType> extraCommitOn,
        Optional<SessionCommit> sessionCommit,
        Optional<Map<String, String>> pipelineOutput,
        Optional<Map<String, Object>> stepInput
) {
}
