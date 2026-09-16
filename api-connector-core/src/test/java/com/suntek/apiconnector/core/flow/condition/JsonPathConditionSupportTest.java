/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.flow.condition;

import com.suntek.apiconnector.core.jsonpath.ForbiddenJsonPathException;
import com.suntek.apiconnector.core.validate.ValidationCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonPathConditionSupportTest {

    @Test
    void filterExpressionIsValJsonpathForbidden() {
        assertThat(JsonPathConditionSupport.forbiddenCode("$.a[?(@.b)]"))
                .contains(ValidationCodes.VAL_JSONPATH_FORBIDDEN);
        assertThatThrownBy(() -> Condition.JsonPathCondition.exists("$.a[?(@.b)]"))
                .isInstanceOf(ForbiddenJsonPathException.class);
    }

    @Test
    void allowedPathHasNoForbiddenCode() {
        assertThat(JsonPathConditionSupport.forbiddenCode("$.error")).isEmpty();
    }
}
