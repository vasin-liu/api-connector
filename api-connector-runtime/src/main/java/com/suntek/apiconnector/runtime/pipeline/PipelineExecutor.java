/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.pipeline;

import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.plan.CompiledPipeline;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.secret.CredentialResolver;
import com.suntek.apiconnector.runtime.secret.SecretSinkPolicy;
import com.suntek.apiconnector.runtime.secret.SinkDestination;
import com.suntek.apiconnector.runtime.state.VariableRuntime;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes a compiled pipeline graph (passthrough, codec.json, concat, sorted-query, hmac-sha256).
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class PipelineExecutor {

    private PipelineExecutor() {
    }

    /**
     * @param pipeline    compiled graph
     * @param plan        owning plan (credentials)
     * @param variables   current variables
     * @param objectInput optional object for codec.json
     * @return port values keyed by {@code nodeId.portKey}
     */
    public static Map<String, DataValue> execute(
            CompiledPipeline pipeline,
            ExecutionPlan plan,
            VariableRuntime variables,
            Optional<DataValue> objectInput,
            CredentialResolver secrets,
            SecretSinkPolicy sinks
    ) {
        Map<String, DataValue> ports = new LinkedHashMap<>();
        for (Map<String, Object> rawNode : pipeline.nodes()) {
            String id = YamlMaps.stringOrNull(rawNode.get("id"));
            String type = YamlMaps.stringOrNull(rawNode.get("type"));
            if (id == null || type == null) {
                continue;
            }
            switch (type) {
                case "passthrough" -> {
                    DataValue in = objectInput.orElse(new DataValue.BytesValue(new byte[0]));
                    ports.put(id + ".out", in);
                }
                case "codec.json" -> {
                    DataValue object = objectInput.orElseGet(() -> executionObject(variables));
                    ports.put(id + ".out", new DataValue.BytesValue(JsonBytes.utf8(object)));
                    ports.put(id + ".body", ports.get(id + ".out"));
                }
                case "canonicalizer.concat" -> {
                    byte[] canonical = concat(YamlMaps.map(rawNode.get("config")), plan, variables, secrets, sinks);
                    ports.put(id + ".out", new DataValue.BytesValue(canonical));
                    ports.put(id + ".canonical", ports.get(id + ".out"));
                }
                case "canonicalizer.sorted-query" -> {
                    byte[] canonical = sortedQuery(YamlMaps.map(rawNode.get("config")), plan, variables, secrets, sinks);
                    ports.put(id + ".out", new DataValue.BytesValue(canonical));
                    ports.put(id + ".canonical", ports.get(id + ".out"));
                }
                case "signer.hmac-sha256" -> {
                    byte[] data = findBytes(ports, "canonicalizer.concat", pipeline);
                    SecretValue keySecret = null;
                    for (Object edgeRaw : pipeline.edges()) {
                        Map<String, Object> edge = YamlMaps.map(edgeRaw);
                        String to = YamlMaps.stringOrNull(edge.get("to"));
                        if (to != null && to.endsWith(".in") && edge.get("from") instanceof String from) {
                            DataValue value = ports.get(from);
                            if (value instanceof DataValue.BytesValue(byte[] bytes)) {
                                data = bytes;
                            }
                        }
                        if (to != null && to.endsWith(".key") && edge.get("from") instanceof Map<?, ?>) {
                            String secretRef = YamlMaps.stringOrNull(YamlMaps.map(edge.get("from")).get("secretRef"));
                            if (secretRef != null) {
                                keySecret = secrets.requireCredential(plan, secretRef, "value");
                            }
                        }
                    }
                    if (keySecret == null) {
                        keySecret = secrets.requireCredential(plan, "apiKey", "value");
                    }
                    sinks.check(keySecret, SecretSink.HMAC, new SinkDestination(plan.definitionId()));
                    byte[][] keyHolder = new byte[1][];
                    keySecret.use(bytes -> keyHolder[0] = bytes);
                    String hex = HmacSha256.hex(keyHolder[0] == null ? new byte[0] : keyHolder[0], data == null ? new byte[0] : data);
                    ports.put(id + ".out", new DataValue.StringValue(hex));
                    ports.put(id + ".hex", ports.get(id + ".out"));
                }
                default -> {
                }
            }
        }
        return Map.copyOf(ports);
    }

    /**
     * @param pipeline  json body pipeline
     * @param plan      plan
     * @param variables vars
     * @param object    object to encode
     * @param secrets   credential resolver
     * @param sinks     sink policy
     * @return body bytes
     */
    public static byte[] encodeJsonBody(
            CompiledPipeline pipeline,
            ExecutionPlan plan,
            VariableRuntime variables,
            DataValue object,
            CredentialResolver secrets,
            SecretSinkPolicy sinks
    ) {
        Map<String, DataValue> ports = execute(pipeline, plan, variables, Optional.of(object), secrets, sinks);
        for (DataValue value : ports.values()) {
            if (value instanceof DataValue.BytesValue(byte[] bytes)) {
                return bytes;
            }
        }
        return JsonBytes.utf8(object);
    }

    private static byte[] concat(
            Map<String, Object> config,
            ExecutionPlan plan,
            VariableRuntime variables,
            CredentialResolver secrets,
            SecretSinkPolicy sinks
    ) {
        String separator = config.get("separator") == null ? "" : String.valueOf(config.get("separator"));
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Object partRaw : YamlMaps.list(config.get("parts"))) {
            if (!first) {
                out.append(separator);
            }
            first = false;
            Map<String, Object> part = YamlMaps.map(partRaw);
            if (part.containsKey("secretRef")) {
                SecretValue secret = secrets.requireCredential(plan, String.valueOf(part.get("secretRef")), "value");
                sinks.check(secret, SecretSink.HMAC, new SinkDestination(plan.definitionId()));
                String[] holder = new String[1];
                secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
                out.append(holder[0] == null ? "" : holder[0]);
            } else if (part.containsKey("var")) {
                out.append(readVar(String.valueOf(part.get("var")), variables));
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sortedQuery(
            Map<String, Object> config,
            ExecutionPlan plan,
            VariableRuntime variables,
            CredentialResolver secrets,
            SecretSinkPolicy sinks
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        YamlMaps.map(config.get("params")).forEach((name, raw) ->
                params.put(name, resolvePart(raw, plan, variables, secrets, sinks)));
        List<String> exclude = new ArrayList<>();
        for (Object item : YamlMaps.list(config.get("exclude"))) {
            if (item != null) {
                exclude.add(String.valueOf(item));
            }
        }
        String separator = config.get("separator") == null ? "&" : String.valueOf(config.get("separator"));
        String encodingRaw = config.get("encoding") == null ? null : String.valueOf(config.get("encoding"));
        String canonical = SortedQueryCanonicalizer.canonicalize(
                params, exclude, separator, SortedQueryCanonicalizer.parseEncoding(encodingRaw));
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    private static String resolvePart(
            Object raw,
            ExecutionPlan plan,
            VariableRuntime variables,
            CredentialResolver secrets,
            SecretSinkPolicy sinks
    ) {
        Map<String, Object> part = raw instanceof Map<?, ?> ? YamlMaps.map(raw) : Map.of("value", raw);
        if (part.containsKey("secretRef")) {
            SecretValue secret = secrets.requireCredential(plan, String.valueOf(part.get("secretRef")), "value");
            sinks.check(secret, SecretSink.HMAC, new SinkDestination(plan.definitionId()));
            String[] holder = new String[1];
            secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
            return holder[0] == null ? "" : holder[0];
        }
        if (part.containsKey("var")) {
            return readVar(String.valueOf(part.get("var")), variables);
        }
        if (part.containsKey("value")) {
            return String.valueOf(part.get("value"));
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    private static String readVar(String path, VariableRuntime variables) {
        int dot = path.indexOf('.');
        if (dot <= 0) {
            return "";
        }
        VariableScope scope = VariableScope.valueOf(path.substring(0, dot).toUpperCase());
        return stringify(variables.get(scope, path.substring(dot + 1)).orElse(new DataValue.NullValue()));
    }

    private static String stringify(DataValue value) {
        return switch (value) {
            case DataValue.StringValue(String v) -> v;
            case DataValue.NumberValue(Number n) -> String.valueOf(n);
            case DataValue.BooleanValue(boolean b) -> String.valueOf(b);
            case DataValue.BytesValue(byte[] bytes) -> new String(bytes, StandardCharsets.UTF_8);
            case SecretValue secret -> {
                String[] holder = new String[1];
                secret.use(bytes -> holder[0] = new String(bytes, StandardCharsets.UTF_8));
                yield holder[0] == null ? "" : holder[0];
            }
            default -> "";
        };
    }

    private static byte[] findBytes(Map<String, DataValue> ports, String unused, CompiledPipeline pipeline) {
        for (DataValue value : ports.values()) {
            if (value instanceof DataValue.BytesValue(byte[] bytes)) {
                return bytes;
            }
        }
        return new byte[0];
    }

    private static DataValue executionObject(VariableRuntime variables) {
        Map<String, DataValue> fields = new LinkedHashMap<>();
        // best-effort: empty object when caller did not supply input
        return new DataValue.ObjectValue(Map.copyOf(fields));
    }
}
