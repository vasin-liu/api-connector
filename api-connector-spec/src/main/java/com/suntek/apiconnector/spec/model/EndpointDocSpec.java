/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

import java.util.Collections;
import java.util.List;

/**
 * 端点文档元数据（OpenAPI 可读性，不影响运行时转发）。
 */
public final class EndpointDocSpec {

    private final String summary;
    private final String description;
    private final String group;
    private final List<EndpointParamSpec> parameters;

    public EndpointDocSpec(String summary, String description, String group, List<EndpointParamSpec> parameters) {
        this.summary = summary;
        this.description = description;
        this.group = group;
        this.parameters = parameters == null ? Collections.emptyList() : List.copyOf(parameters);
    }

    public String summary() {
        return summary;
    }

    public String description() {
        return description;
    }

    public String group() {
        return group;
    }

    public List<EndpointParamSpec> parameters() {
        return parameters;
    }
}
