/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKeyTest {

    @Test
    void lookupIgnoresRevisionAndCompatibilityRequiresProfileAndCredential() {
        SessionKey first = new SessionKey("mock-b", "1", "password-login", "account");
        SessionKey pipelineOnly = new SessionKey("mock-b", "2", "password-login", "account");
        SessionKey profileChanged = new SessionKey("mock-b", "3", "password-login-v2", "account");

        assertThat(first.lookupKey()).isEqualTo(pipelineOnly.lookupKey());
        assertThat(first.equals(pipelineOnly)).isFalse();
        assertThat(pipelineOnly.compatibleWith(first)).isTrue();
        assertThat(profileChanged.lookupKey()).isNotEqualTo(first.lookupKey());
        assertThat(profileChanged.compatibleWith(first)).isFalse();
    }
}
