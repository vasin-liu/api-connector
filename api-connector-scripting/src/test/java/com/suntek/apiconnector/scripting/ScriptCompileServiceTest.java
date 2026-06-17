package com.suntek.apiconnector.scripting;

import org.junit.jupiter.api.Test;

import javax.script.CompiledScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptCompileServiceTest {

    @Test
    void compilesValidScript() throws Exception {
        ScriptCompileService service = new ScriptCompileService();
        CompiledScript script = service.compile("'hello'", "valid");
        assertNotNull(script);
        assertEquals("hello", script.eval());
    }

    @Test
    void secondCompileSameSourceUsesCache() {
        ScriptCompileService service = new ScriptCompileService();
        String source = "42";

        CompiledScript first = service.compile(source, "cache-test");
        CompiledScript second = service.compile(source, "cache-test");

        assertEquals(first, second);
        assertEquals(1, service.cache().size());
    }

    @Test
    void invalidScriptIncludesLineInException() {
        ScriptCompileService service = new ScriptCompileService();
        ScriptCompileException ex = assertThrows(
                ScriptCompileException.class,
                () -> service.compile("def x = {", "broken"));
        assertNotNull(ex.getMessage());
        assertNotNull(ex.label());
    }

    @Test
    void differentSourceDifferentHash() {
        ScriptCompileService service = new ScriptCompileService();
        service.compile("'one'", "a");
        service.compile("'two'", "b");
        assertEquals(2, service.cache().size());
    }
}
