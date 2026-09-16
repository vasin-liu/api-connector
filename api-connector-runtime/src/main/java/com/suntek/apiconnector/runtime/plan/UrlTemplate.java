/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

import com.suntek.apiconnector.core.flow.VariableScope;

import java.util.List;

/**
 * URL template as literals and variable refs. GLOBAL is never inlined.
 *
 * @param parts ordered parts
 * @author Gensokyo
 * @since 2026-09-14
 */
public record UrlTemplate(List<UrlPart> parts) {

    /**
     * One template segment.
     */
    public sealed interface UrlPart permits UrlPart.Literal, UrlPart.VarRef {

        /**
         * @param value literal text
         */
        record Literal(String value) implements UrlPart {
        }

        /**
         * @param scope variable scope
         * @param name  variable name
         */
        record VarRef(VariableScope scope, String name) implements UrlPart {
        }
    }
}
