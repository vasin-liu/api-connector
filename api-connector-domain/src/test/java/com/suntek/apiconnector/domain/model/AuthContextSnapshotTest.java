package com.suntek.apiconnector.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthContextSnapshotTest {

    @Test
    void preservesAccessTokenInExt() {
        Map<String, Object> contextExt = new HashMap<>();
        contextExt.put("accessToken", "tok-abc-123");
        contextExt.put("tokenExpiresAt", "2026-06-18T00:00:00Z");

        AuthContextSnapshot snapshot = AuthContextSnapshot.of(
                "VENDOR",
                List.of("oauth2_client_credentials"),
                Map.of("clientIdRef", "appId", "clientSecretRef", "appSecret"),
                contextExt,
                Map.of("Authorization", "Bearer tok-abc-123"));

        assertEquals("tok-abc-123", snapshot.ext().get("accessToken"));
        assertEquals("2026-06-18T00:00:00Z", snapshot.ext().get("tokenExpiresAt"));
        assertEquals("VENDOR", snapshot.code3rd());
        assertEquals(List.of("oauth2_client_credentials"), snapshot.profileTypes());
    }

    @Test
    void extMapIsUnmodifiable() {
        AuthContextSnapshot snapshot = AuthContextSnapshot.of(
                "VENDOR",
                List.of("none"),
                Map.of(),
                Map.of("accessToken", "x"),
                Map.of());

        assertThrows(UnsupportedOperationException.class, () -> snapshot.ext().put("k", "v"));
    }
}
