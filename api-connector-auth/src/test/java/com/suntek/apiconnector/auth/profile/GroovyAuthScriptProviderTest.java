package com.suntek.apiconnector.auth.profile;

import com.suntek.apiconnector.auth.context.AuthContext;
import com.suntek.apiconnector.auth.context.AuthOutcome;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyAuthScriptProviderTest {

    private final GroovyAuthScriptProvider provider =
            new GroovyAuthScriptProvider(new ScriptCompileService());

    @Test
    void applyReturnsBearerAuthorizationFromInlineScript() {
        Map<String, Object> auth = Map.of(
                "type", "groovy_auth_script",
                "script", """
                        import com.suntek.apiconnector.auth.context.AuthOutcome
                        new AuthOutcome([Authorization: 'Bearer test-token'], [:], null)
                        """);
        AuthContext context = new AuthContext(
                "GROOVY_DEMO",
                "https://vendor.example.com",
                "GET",
                "/api/demo",
                Map.of(),
                null,
                auth,
                Map.of(),
                Map.of());

        AuthOutcome outcome = provider.apply(context);

        assertNotNull(outcome.headers().get("Authorization"));
        assertTrue(outcome.headers().get("Authorization").startsWith("Bearer "));
    }

    @Test
    void applyReturnsBearerAuthorizationFromDemoScriptResource() throws IOException {
        String script = new String(
                getClass().getResourceAsStream("/scripts/demo_auth.groovy").readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, Object> auth = Map.of("type", "groovy_auth_script", "script", script);
        AuthContext context = new AuthContext(
                "GROOVY_DEMO",
                "https://vendor.example.com",
                "GET",
                "/api/demo",
                Map.of(),
                null,
                auth,
                Map.of(),
                Map.of());

        AuthOutcome outcome = provider.apply(context);

        assertNotNull(outcome.headers().get("Authorization"));
        assertTrue(outcome.headers().get("Authorization").startsWith("Bearer test-token"));
    }
}
