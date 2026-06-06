/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.engine.transport;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 JDK {@link HttpClient} 的 HTTP 传输（支持 GZIP 与二进制响应）。
 */
public class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;

    public JdkHttpTransport() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public void exchangeStream(HttpTransportRequest request, HttpStreamHandler handler) {
        String url = buildUrl(request.baseUrl(), request.path(), request.query());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5));
        if (request.headers() != null) {
            request.headers().forEach(builder::header);
        }
        String method = request.method() != null ? request.method() : "GET";
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                    request.body() != null ? request.body() : "",
                    StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<InputStream> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            Map<String, String> headers = toHeaderMap(response.headers().map());
            int status = response.statusCode();
            try (InputStream in = response.body();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handler.onLine(status, headers, line);
                }
            }
            handler.onLine(status, headers, null);
        } catch (Exception e) {
            throw new IllegalStateException("HTTP stream failed: " + url, e);
        }
    }

    @Override
    public HttpTransportResponse exchange(HttpTransportRequest request) {
        String url = buildUrl(request.baseUrl(), request.path(), request.query());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60));
        if (request.headers() != null) {
            request.headers().forEach(builder::header);
        }
        String method = request.method() != null ? request.method() : "GET";
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(
                    request.body() != null ? request.body() : "",
                    StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            Map<String, String> headers = toHeaderMap(response.headers().map());
            HttpBodyDecoder.DecodedBody decoded = HttpBodyDecoder.decode(response.body(), headers);
            return new HttpTransportResponse(
                    response.statusCode(),
                    decoded.text(),
                    decoded.encoding(),
                    headers);
        } catch (Exception e) {
            throw new IllegalStateException("HTTP exchange failed: " + url, e);
        }
    }

    private static Map<String, String> toHeaderMap(Map<String, List<String>> raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) {
            return map;
        }
        raw.forEach((key, values) -> {
            if (key != null && values != null && !values.isEmpty()) {
                map.put(key, String.join(",", values));
            }
        });
        return map;
    }

    private static String buildUrl(String baseUrl, String path, Map<String, String> query) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String url = normalizedBase + normalizedPath;
        if (query == null || query.isEmpty()) {
            return url;
        }
        String qs = query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return url + "?" + qs;
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
