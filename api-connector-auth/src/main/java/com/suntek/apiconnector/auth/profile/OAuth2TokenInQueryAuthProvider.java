/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.auth.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntek.apiconnector.auth.cache.CachedToken;
import com.suntek.apiconnector.auth.cache.TokenCache;
import com.suntek.apiconnector.auth.cache.TokenCacheKey;
import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.context.AuthOutcome;
import com.suntek.apiconnector.auth.spi.AuthProvider;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 Client Credentials，将 access_token 写入 Query（百度文心等）。
 */
public class OAuth2TokenInQueryAuthProvider implements AuthProvider {

    private static final String TYPE = "oauth2_token_in_query";
    private static final String DEFAULT_CLIENT_ID_REF = "appId";
    private static final String DEFAULT_CLIENT_SECRET_REF = "appSecret";
    private static final String DEFAULT_TOKEN_PARAM = "access_token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenCache tokenCache;

    public OAuth2TokenInQueryAuthProvider(TokenCache tokenCache) {
        this.tokenCache = tokenCache;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String profileType() {
        return TYPE;
    }

    @Override
    public AuthOutcome apply(AuthContext context) {
        String token = resolveAccessToken(context);
        String paramName = stringConfig(context.authConfig(), "tokenParam", DEFAULT_TOKEN_PARAM);
        Map<String, String> query = new HashMap<>(context.query());
        query.put(paramName, token);
        return new AuthOutcome(Map.of(), query, null);
    }

    private String resolveAccessToken(AuthContext context) {
        String scope = stringConfig(context.authConfig(), "scope", "");
        TokenCacheKey cacheKey = new TokenCacheKey(context.code3rd(), TYPE, scope);
        CachedToken cached = tokenCache.getOrRefresh(cacheKey, () -> fetchToken(context));
        populateExt(context.ext(), cached);
        return cached.accessToken();
    }

    private CachedToken fetchToken(AuthContext context) {
        String clientIdRef = stringConfig(context.authConfig(), "clientIdRef", DEFAULT_CLIENT_ID_REF);
        String clientSecretRef = stringConfig(context.authConfig(), "clientSecretRef", DEFAULT_CLIENT_SECRET_REF);
        String clientId = requireCredential(context.credentials(), clientIdRef);
        String clientSecret = requireCredential(context.credentials(), clientSecretRef);
        String tokenUrl = stringConfig(context.authConfig(), "tokenUrl", "/oauth/2.0/token");

        String base = context.baseUrl();
        String url = tokenUrl.startsWith("http") ? tokenUrl : base + tokenUrl;

        String body = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OAuth2 token request failed: HTTP " + response.statusCode());
            }
            String rawJson = response.body();
            JsonNode json = objectMapper.readTree(rawJson);
            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("OAuth2 response missing access_token");
            }
            long expiresIn = json.path("expires_in").asLong(3600);
            return new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn), rawJson);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OAuth2 token request error: " + ex.getMessage(), ex);
        }
    }

    private static void populateExt(Map<String, Object> ext, CachedToken token) {
        if (ext == null) {
            return;
        }
        ext.put("accessToken", token.accessToken());
        ext.put("tokenExpiresAt", token.expiresAt().toString());
        if (token.rawResponse() != null) {
            ext.put("oauthRawResponse", token.rawResponse());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stringConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String requireCredential(Map<String, String> credentials, String ref) {
        String value = credentials.get(ref);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing credential for ref: " + ref);
        }
        return value;
    }
}
