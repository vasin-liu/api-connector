package com.suntek.apiconnector.scripting;

import javax.script.CompiledScript;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store of compiled Groovy scripts keyed by content SHA-256 hex.
 */
public final class CompiledScriptCache {

    private final ConcurrentHashMap<String, CompiledScript> scripts = new ConcurrentHashMap<>();

    public CompiledScript get(String hash) {
        return scripts.get(hash);
    }

    public void put(String hash, CompiledScript script) {
        scripts.put(hash, script);
    }

    public void evict(String hash) {
        scripts.remove(hash);
    }

    public int size() {
        return scripts.size();
    }
}
