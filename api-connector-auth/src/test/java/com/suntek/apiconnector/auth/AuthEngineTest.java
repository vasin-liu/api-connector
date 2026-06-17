package com.suntek.apiconnector.auth;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.exception.AuthErrorCode;
import com.suntek.apiconnector.auth.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthEngineTest {

    @Test
    void missingProviderThrowsAuthProfileMissing() {
        AuthEngine engine = new AuthEngine(List.of());
        AuthContext context = new AuthContext(
                "TEST",
                "https://example.com",
                "GET",
                "/",
                Map.of(),
                null,
                Map.of("type", "nonexistent_profile"),
                Map.of(),
                Map.of());

        AuthException ex = assertThrows(AuthException.class, () -> engine.authenticate(context));

        assertEquals(AuthErrorCode.AUTH_PROFILE_MISSING, ex.code());
        assertEquals("nonexistent_profile", ex.details().get("profileType"));
        assertEquals("TEST", ex.details().get("code3rd"));
    }
}
