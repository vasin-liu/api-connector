package com.suntek.apiconnector.api.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.api.dto.ProxyInvokeResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCompatResponseFormatterTest {

    private final LegacyCompatResponseFormatter formatter =
            new LegacyCompatResponseFormatter(new ObjectMapper());

    @Test
    void vendorRawReturnsBody() {
        ProxyInvokeResponse body = ProxyInvokeResponse.builder()
                .success(true)
                .rawBody("{\"success\":true,\"obj\":1}")
                .build();
        assertThat(formatter.format(body, LegacyResponseStyle.VENDOR_RAW))
                .isEqualTo("{\"success\":true,\"obj\":1}");
    }

    @Test
    void suntekResultWrapsParsedData() {
        ProxyInvokeResponse body = ProxyInvokeResponse.builder()
                .success(true)
                .vendorHttpStatus(200)
                .data(java.util.Map.of("k", 1))
                .build();
        String json = formatter.format(body, LegacyResponseStyle.SUNTEK_RESULT);
        assertThat(json).contains("\"code\":\"200\"");
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"k\":1");
    }
}
