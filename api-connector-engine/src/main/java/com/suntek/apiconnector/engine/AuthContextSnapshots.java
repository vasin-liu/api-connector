/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.engine;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.AuthOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link AuthContextSnapshot} from post-auth engine types (avoids domain→auth compile cycle).
 */
public final class AuthContextSnapshots {

    private AuthContextSnapshots() {
    }

    /**
     * Creates an immutable snapshot after {@code authEngine.authenticate()}.
     *
     * @param ctx           auth context used for authentication
     * @param outcome       merged auth outcome
     * @param profileTypes  profile types applied from resolved auth config
     * @return read-only snapshot for pipeline carry-forward
     */
    public static AuthContextSnapshot from(AuthContext ctx, AuthOutcome outcome, List<String> profileTypes) {
        return AuthContextSnapshot.of(
                ctx.code3rd(),
                profileTypes,
                extractCredentialRefs(ctx.authConfig()),
                ctx.ext(),
                outcome != null ? outcome.headers() : Map.of());
    }

    /**
     * Extracts profile type ids from connector or endpoint auth config.
     *
     * @param authConfig resolved auth configuration
     * @return ordered profile type list
     */
    @SuppressWarnings("unchecked")
    public static List<String> profileTypesFrom(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return List.of();
        }
        Object pipeline = authConfig.get("pipeline");
        if (pipeline instanceof List<?> steps) {
            List<String> types = new ArrayList<>();
            for (Object step : steps) {
                if (step instanceof Map<?, ?> stepMap) {
                    Object type = stepMap.get("type");
                    if (type != null) {
                        types.add(String.valueOf(type));
                    }
                }
            }
            return List.copyOf(types);
        }
        Object type = authConfig.get("type");
        return type == null ? List.of() : List.of(String.valueOf(type));
    }

    private static Map<String, String> extractCredentialRefs(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return Map.of();
        }
        Map<String, String> refs = new LinkedHashMap<>();
        collectRefs(authConfig, refs);
        Object pipeline = authConfig.get("pipeline");
        if (pipeline instanceof List<?> steps) {
            for (Object step : steps) {
                if (step instanceof Map<?, ?> stepMap) {
                    collectRefs((Map<String, Object>) stepMap, refs);
                }
            }
        }
        return Map.copyOf(refs);
    }

    private static void collectRefs(Map<String, Object> config, Map<String, String> refs) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("Ref") && entry.getValue() != null) {
                refs.put(key, String.valueOf(entry.getValue()));
            }
        }
    }
}
