/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.validate;

import java.util.List;

/**
 * Thrown when Validate rejects a definition. No ExecutionPlan is produced.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class DefinitionValidationException extends IllegalArgumentException {

    private final List<Violation> violations;

    /**
     * @param violations all violations
     */
    public DefinitionValidationException(List<Violation> violations) {
        super("VALIDATION_FAILED: " + violations.stream().map(Violation::code).toList());
        this.violations = List.copyOf(violations);
    }

    /**
     * @return all violations
     */
    public List<Violation> violations() {
        return violations;
    }
}
