/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills Normalize defaults from {@code 07-plan-compiler.md} section Q.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class DefinitionNormalizer {

    private static final Pattern TEMPLATE = Pattern.compile("\\{([A-Za-z]+)\\.([A-Za-z0-9_]+)\\}");

    private DefinitionNormalizer() {
    }

    /**
     * @param parsed YAML root
     * @return normalized tree used for planId hashing
     */
    public static Map<String, Object> normalize(Map<String, Object> parsed) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> schema = YamlMaps.map(parsed.get("schema"));
        Map<String, Object> definition = YamlMaps.map(parsed.get("definition"));
        String definitionId = YamlMaps.stringOrNull(definition.get("id"));

        root.put("schema", Map.of("version", YamlMaps.integer(schema.get("version"), 0)));
        root.put("definition", normalizeDefinition(definition));
        root.put("credentials", normalizeCredentials(YamlMaps.map(parsed.get("credentials")), definitionId));
        root.put("variables", YamlMaps.map(parsed.get("variables")));
        if (parsed.get("session") != null) {
            root.put("session", YamlMaps.map(parsed.get("session")));
        }
        root.put("limits", YamlMaps.map(parsed.get("limits")));
        root.put("requests", normalizeRequests(YamlMaps.map(parsed.get("requests"))));
        root.put("pipelines", YamlMaps.map(parsed.get("pipelines")));
        if (parsed.get("policy") != null) {
            root.put("policy", YamlMaps.map(parsed.get("policy")));
        }
        root.put("flows", normalizeFlows(YamlMaps.map(parsed.get("flows"))));
        return root;
    }

    private static Map<String, Object> normalizeDefinition(Map<String, Object> definition) {
        Map<String, Object> out = new LinkedHashMap<>(definition);
        Object revision = definition.get("revision");
        if (revision != null) {
            out.put("revision", String.valueOf(revision));
        }
        return out;
    }

    private static Map<String, Object> normalizeCredentials(Map<String, Object> credentials, String definitionId) {
        Map<String, Object> out = new LinkedHashMap<>();
        credentials.forEach((name, raw) -> {
            Map<String, Object> cred = new LinkedHashMap<>(YamlMaps.map(raw));
            if (!cred.containsKey("apiId") || cred.get("apiId") == null || String.valueOf(cred.get("apiId")).isBlank()) {
                cred.put("apiId", definitionId);
            }
            out.put(name, cred);
        });
        return out;
    }

    private static Map<String, Object> normalizeRequests(Map<String, Object> requests) {
        Map<String, Object> out = new LinkedHashMap<>();
        requests.forEach((id, raw) -> out.put(id, normalizeRequest(id, YamlMaps.map(raw))));
        return out;
    }

    private static Map<String, Object> normalizeRequest(String requestId, Map<String, Object> request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", requestId);
        out.put("method", request.get("method"));
        out.put("url", parseTemplate(YamlMaps.stringOrNull(request.get("url"))));
        out.put("headers", normalizeNamedValues(YamlMaps.map(request.get("headers")), true));
        out.put("query", normalizeNamedValues(YamlMaps.map(request.get("query")), false));
        if (request.get("body") != null) {
            out.put("body", request.get("body"));
        }
        if (request.get("cookies") != null) {
            out.put("cookies", request.get("cookies"));
        }
        out.put("replay", normalizeReplay(YamlMaps.map(request.get("replay"))));
        return out;
    }

    private static Map<String, Object> normalizeReplay(Map<String, Object> replay) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("replayability", replay.get("replayability") == null ? "UNKNOWN" : String.valueOf(replay.get("replayability")));
        out.put("allowAutomaticReplay", YamlMaps.bool(replay.get("allowAutomaticReplay"), false));
        out.put("maxAttempts", YamlMaps.integer(replay.get("maxAttempts"), 1));
        return out;
    }

    private static List<Map<String, Object>> normalizeNamedValues(Map<String, Object> entries, boolean header) {
        List<Map<String, Object>> list = new ArrayList<>();
        entries.forEach((name, raw) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            if (header) {
                item.put("nameLower", name.toLowerCase(Locale.ROOT));
            }
            if (raw instanceof Map<?, ?>) {
                item.putAll(YamlMaps.map(raw));
            } else {
                item.put("value", raw);
            }
            list.add(item);
        });
        return list;
    }

    private static Map<String, Object> normalizeFlows(Map<String, Object> flows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("business", normalizeFlow("business", YamlMaps.map(flows.get("business"))));
        Map<String, Object> authentication = YamlMaps.map(flows.get("authentication"));
        List<Object> authSteps = YamlMaps.list(authentication.get("steps"));
        if (!authSteps.isEmpty()) {
            out.put("authentication", normalizeFlow("authentication", authentication));
        }
        return out;
    }

    private static Map<String, Object> normalizeFlow(String flowId, Map<String, Object> flow) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", flowId);
        List<Object> steps = YamlMaps.list(flow.get("steps"));
        List<Map<String, Object>> normalizedSteps = new ArrayList<>();
        for (Object stepRaw : steps) {
            Map<String, Object> step = new LinkedHashMap<>(YamlMaps.map(stepRaw));
            List<Object> transitions = YamlMaps.list(step.get("transitions"));
            List<Map<String, Object>> normalizedTransitions = new ArrayList<>();
            for (int i = 0; i < transitions.size(); i++) {
                Map<String, Object> t = new LinkedHashMap<>(YamlMaps.map(transitions.get(i)));
                if (t.get("id") == null) {
                    t.put("id", "t" + i);
                }
                normalizedTransitions.add(t);
            }
            step.put("transitions", normalizedTransitions);
            normalizedSteps.add(step);
        }
        out.put("steps", normalizedSteps);
        return out;
    }

    /**
     * @param template URL or header template using {@code {scope.name}}
     * @return structured parts
     */
    public static List<Map<String, Object>> parseTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        Matcher matcher = TEMPLATE.matcher(template);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                parts.add(literal(template.substring(last, matcher.start())));
            }
            parts.add(varRef(scope(matcher.group(1)), matcher.group(2)));
            last = matcher.end();
        }
        if (last < template.length()) {
            parts.add(literal(template.substring(last)));
        }
        return parts;
    }

    private static Map<String, Object> literal(String value) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("kind", "LIT");
        part.put("value", value);
        return part;
    }

    private static Map<String, Object> varRef(VariableScope scope, String name) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("kind", "VAR");
        part.put("scope", scope.name());
        part.put("name", name);
        return part;
    }

    private static VariableScope scope(String raw) {
        return VariableScope.valueOf(raw.toUpperCase(Locale.ROOT));
    }
}
