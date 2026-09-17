/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import java.util.List;
import java.util.Map;

/**
 * Compiled pipeline graph. Type/cycle checks land in later tasks.
 *
 * @param pipelineId id
 * @param nodes      nodes in definition order
 * @param edges      edges
 * @author Gensokyo
 * @since 2026-09-14
 */
public record CompiledPipeline(String pipelineId, List<Map<String, Object>> nodes, List<Object> edges) {
}
