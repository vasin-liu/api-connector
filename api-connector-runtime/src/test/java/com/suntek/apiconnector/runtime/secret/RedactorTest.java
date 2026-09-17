/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.runtime.value.ByteSecret;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void redactsSecretMaterialFromLogsExceptionsAndScriptOutput() {
        var secret = ByteSecret.utf8(new SecretMetadata("secret/mock-b/password", "mock-b"), "s3cret");
        String log = "login failed password=s3cret";
        String exception = "ExecuteException: body contained s3cret";
        String script = "return 's3cret';";
        assertThat(Redactor.redact(log, List.of(secret))).isEqualTo("login failed password=***");
        assertThat(Redactor.redact(exception, List.of(secret))).doesNotContain("s3cret");
        assertThat(Redactor.redact(script, List.of(secret))).isEqualTo("return '***';");
    }

    @Test
    void authorizationHeaderValueIsMasked() {
        assertThat(Redactor.redactHeader("Authorization", "HMAC-SHA256 deadbeef")).isEqualTo("***");
        assertThat(Redactor.redactHeader("X-Timestamp", "1001")).isEqualTo("1001");
    }
}
