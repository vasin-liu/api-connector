/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.spec.model;

/**
 * 端点参数说明（OpenAPI / 试调表单）。
 */
public final class EndpointParamSpec {

    private final String name;
    private final String description;
    private final Boolean required;
    private final String example;
    private final String in;

    public EndpointParamSpec(String name, String description, Boolean required, String example, String in) {
        this.name = name;
        this.description = description;
        this.required = required;
        this.example = example;
        this.in = in;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Boolean required() {
        return required;
    }

    public String example() {
        return example;
    }

    /** query / header / body，默认 query */
    public String in() {
        return in;
    }
}
