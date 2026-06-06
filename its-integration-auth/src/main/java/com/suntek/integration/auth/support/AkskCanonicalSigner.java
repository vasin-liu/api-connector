/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.integration.auth.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * IDPS / Traffic style AK/SK canonical HMAC-SHA256 signer.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class AkskCanonicalSigner {

    public static final String ALGORITHM_NAME = "aksk_hmac_sha256";
    public static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final String HMAC_SHA256 = "HmacSHA256";

    private AkskCanonicalSigner() {
    }

    /**
     * Builds signed authentication headers.
     *
     * @param method           HTTP method
     * @param uri              request URI path (with leading slash)
     * @param query            query parameters
     * @param accessKeyId      access key
     * @param secretAccessKey  secret key
     * @return header map including X-Auth-* entries
     */
    public static Map<String, String> sign(
            String method,
            String uri,
            Map<String, String> query,
            String accessKeyId,
            String secretAccessKey) {
        String dateTimeStr = ZonedDateTime.now().format(TIME_FORMATTER);
        String paramsStr = buildCanonicalQueryString(query);
        String snowflakeId = UUID.randomUUID().toString().replace("-", "");

        String stringToSign = ALGORITHM_NAME + "\n"
                + accessKeyId + "\n"
                + dateTimeStr + "\n"
                + method.toUpperCase() + "\n"
                + uri + "\n"
                + paramsStr + "\n"
                + snowflakeId;

        String signature = hmacSha256Hex(secretAccessKey, stringToSign);

        return Map.of(
                "X-Auth-Key", accessKeyId,
                "X-Auth-Algorithm", ALGORITHM_NAME,
                "X-Auth-Signature", signature,
                "X-Auth-SnowflakeID", snowflakeId,
                "X-Auth-Timestamp", dateTimeStr);
    }

    /**
     * Builds canonical query string sorted by encoded key=value.
     *
     * @param query query map
     * @return canonical string
     */
    public static String buildCanonicalQueryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>(query);
        List<String> encodedItems = new ArrayList<>();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String encodedKey = uriEncode(entry.getKey());
            String encodedValue = entry.getValue() == null ? "" : uriEncode(entry.getValue());
            encodedItems.add(encodedKey + "=" + encodedValue);
        }
        Collections.sort(encodedItems);
        return String.join("&", encodedItems);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 signing failed", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String uriEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
