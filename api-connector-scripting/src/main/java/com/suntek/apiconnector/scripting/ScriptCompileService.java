package com.suntek.apiconnector.scripting;

import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.control.CompilationFailedException;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Compiles Groovy scripts once via JSR-223 and caches {@link CompiledScript} by content hash.
 */
public final class ScriptCompileService {

    private final CompiledScriptCache cache;
    private final ScriptEngine scriptEngine;

    public ScriptCompileService() {
        this(new CompiledScriptCache());
    }

    public ScriptCompileService(CompiledScriptCache cache) {
        this.cache = cache;
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
        if (engine == null) {
            throw new IllegalStateException("Groovy JSR-223 engine not found on classpath");
        }
        this.scriptEngine = engine;
    }

    public CompiledScript compile(String source, String label) {
        String hash = sha256Hex(source);
        CompiledScript cached = cache.get(hash);
        if (cached != null) {
            return cached;
        }

        try {
            CompiledScript compiled = ((Compilable) scriptEngine).compile(source);
            cache.put(hash, compiled);
            return compiled;
        } catch (CompilationFailedException ex) {
            throw new ScriptCompileException(label, extractLine(ex), ex.getMessage(), ex);
        } catch (GroovyRuntimeException ex) {
            throw new ScriptCompileException(label, null, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ScriptCompileException(label, null, ex.getMessage(), ex);
        }
    }

    CompiledScriptCache cache() {
        return cache;
    }

    static String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static Integer extractLine(CompilationFailedException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("@ line (\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
