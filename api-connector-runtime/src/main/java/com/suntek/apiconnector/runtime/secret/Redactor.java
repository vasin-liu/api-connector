/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.value.SecretValue;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;

/**
 * Removes secret material from logs, traces, exceptions, and generic script output.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class Redactor {

    public static final String MASK = "***";

    private Redactor() {
    }

    /**
     * @param text    raw text
     * @param secrets known secrets
     * @return text with material replaced
     */
    public static String redact(String text, Collection<? extends SecretValue> secrets) {
        if (text == null || text.isEmpty() || secrets == null || secrets.isEmpty()) {
            return text == null ? "" : text;
        }
        String out = text;
        for (SecretValue secret : secrets) {
            String[] holder = new String[1];
            secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
            if (holder[0] != null && !holder[0].isEmpty()) {
                out = out.replace(holder[0], MASK);
            }
        }
        return out;
    }

    /**
     * @param headerName HTTP header name
     * @param value      header value
     * @return redacted value for sensitive headers
     */
    public static String redactHeader(String headerName, String value) {
        if (headerName != null) {
            String lower = headerName.toLowerCase(Locale.ROOT);
            if ("authorization".equals(lower) || "cookie".equals(lower) || "set-cookie".equals(lower)) {
                return MASK;
            }
        }
        return value == null ? "" : value;
    }
}
