/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 解码 HTTP 响应体（GZIP + 文本/二进制）。
 */
public final class HttpBodyDecoder {

    private HttpBodyDecoder() {
    }

    public record DecodedBody(String text, String encoding) {
    }

    public static DecodedBody decode(byte[] bytes, Map<String, String> headers) {
        if (bytes == null || bytes.length == 0) {
            return new DecodedBody("", null);
        }
        byte[] payload = bytes;
        String encoding = header(headers, "Content-Encoding");
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
            payload = gunzip(bytes);
        }
        String contentType = header(headers, "Content-Type");
        if (isBinaryContentType(contentType)) {
            return new DecodedBody(Base64.getEncoder().encodeToString(payload), "base64");
        }
        Charset charset = charsetFromContentType(contentType);
        return new DecodedBody(new String(payload, charset), null);
    }

    private static boolean isBinaryContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("octet-stream")
                || lower.startsWith("image/")
                || lower.startsWith("audio/")
                || lower.startsWith("video/")
                || lower.contains("application/gzip");
    }

    private static byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzip.transferTo(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decompress gzip response", ex);
        }
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("charset=")) {
                return Charset.forName(trimmed.substring("charset=".length()));
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
