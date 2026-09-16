/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import com.suntek.apiconnector.core.session.SessionKey;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable compiled plan. Runtime must not read YAML.
 *
 * @param planId               sha256 of canonical normalized JSON
 * @param definitionId         definition.id
 * @param definitionRevision   definition.revision
 * @param authProfile          auth profile id
 * @param credentialRef        session lookup credential key
 * @param credentials          compiled credential declarations
 * @param variables            declared variables
 * @param limits               limits
 * @param sessionPolicy        session policy
 * @param executionPolicy      execution policy
 * @param requests             compiled requests
 * @param pipelines            compiled pipelines
 * @param business             business flow
 * @param authentication       authentication flow when present
 * @param capabilities         capability flags
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ExecutionPlan(
        String planId,
        String definitionId,
        String definitionRevision,
        String authProfile,
        String credentialRef,
        Map<String, CompiledCredential> credentials,
        Map<String, CompiledVariable> variables,
        Limits limits,
        SessionPolicy sessionPolicy,
        ExecutionPolicy executionPolicy,
        Map<String, CompiledRequest> requests,
        Map<String, CompiledPipeline> pipelines,
        CompiledFlow business,
        Optional<CompiledFlow> authentication,
        Set<PlanCapability> capabilities
) {

    /**
     * @return recorded SessionKey for this plan (includes revision)
     */
    public SessionKey sessionKey() {
        return new SessionKey(definitionId, definitionRevision, authProfile, credentialRef);
    }
}
