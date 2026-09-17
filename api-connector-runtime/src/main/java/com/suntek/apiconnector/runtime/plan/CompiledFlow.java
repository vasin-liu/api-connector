/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.List;

/**
 * Compiled flow.
 *
 * @param flowId           flow id
 * @param role             BUSINESS or AUTHENTICATION
 * @param steps            steps in order
 * @param transitionLimit  copied from limits
 * @author Gensokyo
 * @since 2026-09-14
 */
public record CompiledFlow(String flowId, FlowRole role, List<CompiledStep> steps, int transitionLimit) {
}
