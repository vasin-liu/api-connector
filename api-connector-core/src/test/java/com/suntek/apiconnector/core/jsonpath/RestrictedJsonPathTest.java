/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.jsonpath;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestrictedJsonPathTest {

    @Test
    void rejectsFilterExpression() {
        assertThatThrownBy(() -> RestrictedJsonPath.validate("$.a[?(@.b)]"))
                .isInstanceOf(ForbiddenJsonPathException.class);
    }

    @Test
    void rejectsRecursiveDescent() {
        assertThatThrownBy(() -> RestrictedJsonPath.validate("$..name"))
                .isInstanceOf(ForbiddenJsonPathException.class);
    }

    @Test
    void rejectsFunctionCall() {
        assertThatThrownBy(() -> RestrictedJsonPath.validate("$.length()"))
                .isInstanceOf(ForbiddenJsonPathException.class);
    }

    @Test
    void acceptsRootPropertyAndIndex() {
        assertThatCode(() -> RestrictedJsonPath.validate("$.error")).doesNotThrowAnyException();
        assertThatCode(() -> RestrictedJsonPath.validate("$.items[0].name")).doesNotThrowAnyException();
        assertThatCode(() -> RestrictedJsonPath.validate("$")).doesNotThrowAnyException();
    }

    @Test
    void selectRootProperty() {
        RestrictedJsonPath.JsonSelect selected = RestrictedJsonPath.select(
                "{\"error\":\"PERMISSION_DENIED\"}",
                "$.error"
        );
        assertThat(selected).isEqualTo(RestrictedJsonPath.JsonSelect.found("PERMISSION_DENIED"));
    }

    @Test
    void selectMissingIsMissing() {
        assertThat(RestrictedJsonPath.select("{\"error\":\"OTHER\"}", "$.missing"))
                .isEqualTo(RestrictedJsonPath.JsonSelect.missing());
    }

    @Test
    void selectNonJsonIsMissing() {
        assertThat(RestrictedJsonPath.select("not-json", "$.error"))
                .isEqualTo(RestrictedJsonPath.JsonSelect.missing());
    }
}
