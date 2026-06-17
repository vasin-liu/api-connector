/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable post-authentication view for mapping and audit (D-10, D-11).
 *
 * <p>Standard {@code ext} keys: {@code accessToken}, {@code tokenExpiresAt},
 * {@code oauthRawResponse}, {@code signatureBase}, {@code appliedHeaders}.
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-17
 */
public record AuthContextSnapshot(
        String code3rd,
        List<String> profileTypes,
        Map<String, String> credentialRefsUsed,
        Map<String, Object> ext) {

    private static final List<String> STANDARD_EXT_KEYS = List.of(
            "accessToken", "tokenExpiresAt", "oauthRawResponse", "signatureBase");

    public AuthContextSnapshot {
        profileTypes = List.copyOf(profileTypes);
        credentialRefsUsed = Map.copyOf(credentialRefsUsed);
        ext = Map.copyOf(ext);
    }

    /**
     * Builds a snapshot from resolved auth inputs (engine calls after authenticate).
     *
     * @param code3rd              connector id
     * @param profileTypes         applied profile type ids
     * @param credentialRefsUsed   credential ref names from auth config (not secret values)
     * @param contextExt           mutable ext map populated during auth
     * @param appliedHeaders       merged auth headers for audit
     * @return immutable snapshot
     */
    public static AuthContextSnapshot of(
            String code3rd,
            List<String> profileTypes,
            Map<String, String> credentialRefsUsed,
            Map<String, Object> contextExt,
            Map<String, String> appliedHeaders) {
        Map<String, Object> ext = new LinkedHashMap<>();
        if (contextExt != null) {
            ext.putAll(contextExt);
        }
        for (String key : STANDARD_EXT_KEYS) {
            if (contextExt != null && contextExt.containsKey(key)) {
                ext.put(key, contextExt.get(key));
            }
        }
        if (appliedHeaders != null && !appliedHeaders.isEmpty()) {
            ext.put("appliedHeaders", Map.copyOf(appliedHeaders));
        }
        return new AuthContextSnapshot(code3rd, profileTypes, credentialRefsUsed, ext);
    }
}
