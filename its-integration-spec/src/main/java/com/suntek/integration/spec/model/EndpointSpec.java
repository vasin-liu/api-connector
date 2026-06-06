/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.spec.model;

/**
 * Single HTTP endpoint definition.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class EndpointSpec {

    private final String id;
    private final String method;
    private final String path;
    private final String bodyTemplate;
    private final Boolean enabled;
    private final EndpointDocSpec doc;

    public EndpointSpec(
            String id,
            String method,
            String path,
            String bodyTemplate,
            Boolean enabled,
            EndpointDocSpec doc) {
        this.id = id;
        this.method = method;
        this.path = path;
        this.bodyTemplate = bodyTemplate;
        this.enabled = enabled;
        this.doc = doc;
    }

    /** 兼容旧构造（无 doc）。 */
    public EndpointSpec(String id, String method, String path, String bodyTemplate, Boolean enabled) {
        this(id, method, path, bodyTemplate, enabled, null);
    }

    public String id() {
        return id;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String bodyTemplate() {
        return bodyTemplate;
    }

    public Boolean enabled() {
        return enabled;
    }

    public EndpointDocSpec doc() {
        return doc;
    }
}
