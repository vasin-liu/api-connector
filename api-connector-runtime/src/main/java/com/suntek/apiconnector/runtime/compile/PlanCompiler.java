/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.validate.DefinitionValidationException;
import com.suntek.apiconnector.core.validate.Violation;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.validate.DefinitionValidator;
import com.suntek.apiconnector.runtime.yaml.YamlDefinitionParser;

import java.util.List;
import java.util.Map;

/**
 * YAML → Normalize → planId → ExecutionPlan. Runtime never re-reads YAML.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class PlanCompiler {

    private PlanCompiler() {
    }

    /**
     * @param yaml Canonical Definition YAML
     * @return compiled plan
     */
    public static ExecutionPlan compile(String yaml) {
        Map<String, Object> parsed = YamlDefinitionParser.parse(yaml);
        Map<String, Object> normalized = DefinitionNormalizer.normalize(parsed);
        List<Violation> violations = DefinitionValidator.validate(normalized);
        if (!violations.isEmpty()) {
            throw new DefinitionValidationException(violations);
        }
        String planId = PlanId.sha256Hex(CanonicalJson.stringify(normalized));
        return PlanAssembler.assemble(planId, normalized);
    }
}
