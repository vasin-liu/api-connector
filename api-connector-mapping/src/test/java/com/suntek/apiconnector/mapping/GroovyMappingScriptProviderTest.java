/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.apiconnector.mapping;

import com.jayway.jsonpath.JsonPath;
import com.suntek.apiconnector.domain.model.AuthContextSnapshot;
import com.suntek.apiconnector.domain.model.EndpointMeta;
import com.suntek.apiconnector.domain.model.MappingContext;
import com.suntek.apiconnector.domain.model.MappingDirection;
import com.suntek.apiconnector.mapping.exception.MappingErrorCode;
import com.suntek.apiconnector.mapping.exception.MappingException;
import com.suntek.apiconnector.scripting.ScriptCompileService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroovyMappingScriptProviderTest {

    @Test
    void applyTransformsVendorBodyToLegacyShape() {
        GroovyMappingScriptProvider provider = new GroovyMappingScriptProvider(new ScriptCompileService());
        MappingContext ctx = context(
                MappingDirection.RESPONSE,
                "{\"result\":\"vendor-payload\"}",
                Map.of());

        String json = provider.applyAsJson(
                ctx,
                """
                        def m = ctx.bodyAsMap()
                        [code:'200', success:true, data:m.result]
                        """,
                "TEST:mapping:response");

        assertEquals("200", JsonPath.read(json, "$.code"));
        assertEquals(true, JsonPath.read(json, "$.success"));
        assertEquals("vendor-payload", JsonPath.read(json, "$.data"));
    }

    @Test
    void applyReadsAuthSnapshotExtAccessToken() {
        GroovyMappingScriptProvider provider = new GroovyMappingScriptProvider(new ScriptCompileService());
        MappingContext ctx = context(
                MappingDirection.REQUEST,
                "{}",
                Map.of("accessToken", "publish-token-abc"));

        String json = provider.applyAsJson(
                ctx,
                """
                        def token = ctx.authSnapshot().ext().get('accessToken')
                        [Authorization: 'Bearer ' + token]
                        """,
                "TEST:mapping:request");

        assertEquals("Bearer publish-token-abc", JsonPath.read(json, "$.Authorization"));
    }

    @Test
    void applyRejectsInvalidReturnType() {
        GroovyMappingScriptProvider provider = new GroovyMappingScriptProvider(new ScriptCompileService());
        MappingContext ctx = context(MappingDirection.RESPONSE, "{}", Map.of());

        MappingException ex = assertThrows(
                MappingException.class,
                () -> provider.applyAsJson(ctx, "return 'not-a-map'", "TEST:mapping:response"));

        assertEquals(MappingErrorCode.MAPPING_SCRIPT_RUNTIME_ERROR, ex.code());
    }

    @Test
    void secondEvalReusesCompiledScriptCache() {
        ScriptCompileService compileService = new ScriptCompileService();
        GroovyMappingScriptProvider provider = new GroovyMappingScriptProvider(compileService);
        MappingContext ctx = context(MappingDirection.RESPONSE, "{\"result\":\"ok\"}", Map.of());
        String script = """
                def m = ctx.bodyAsMap()
                [code:'200', success:true, data:m.result]
                """;
        String label = "TEST:mapping:response";

        provider.applyAsJson(ctx, script, label);
        assertEquals(1, compileService.compiledScriptCacheSize());

        provider.applyAsJson(ctx, script, label);
        assertEquals(1, compileService.compiledScriptCacheSize());
    }

    private static MappingContext context(
            MappingDirection direction, String body, Map<String, Object> ext) {
        AuthContextSnapshot snapshot = new AuthContextSnapshot(
                "TEST", List.of("oauth2_client_credentials"), Map.of(), ext);
        return new MappingContext(
                "TEST",
                direction,
                body,
                snapshot,
                new EndpointMeta("invoke", "GET", "/api/demo"));
    }
}
