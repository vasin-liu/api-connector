/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.validate;

import com.suntek.apiconnector.core.validate.ValidationCodes;
import com.suntek.apiconnector.core.validate.Violation;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline graph checks from {@code 07-plan-compiler.md} table W (P0–P7).
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class PipelineGraphValidator {

    private static final Set<String> KNOWN = Set.of(
            "passthrough",
            "codec.json",
            "canonicalizer.concat",
            "canonicalizer.sorted-query",
            "signer.hmac-sha256"
    );

    private PipelineGraphValidator() {
    }

    /**
     * @param normalized Normalize output
     * @param violations sink
     */
    public static void validate(Map<String, Object> normalized, List<Violation> violations) {
        Map<String, Object> pipelines = YamlMaps.map(normalized.get("pipelines"));
        Map<String, Set<String>> nodesByPipeline = new HashMap<>();
        pipelines.forEach((pipelineId, raw) -> {
            Map<String, Object> pipe = YamlMaps.map(raw);
            String path = "/pipelines/" + pipelineId;
            Map<String, Node> nodes = parseNodes(pipe, path, violations);
            nodesByPipeline.put(pipelineId, nodes.keySet());
            List<Edge> edges = parseEdges(YamlMaps.list(pipe.get("edges")), path, nodes, violations);
            checkUnknown(nodes, path, violations);
            markConfigBindings(nodes);
            checkCardinalityAndTypes(nodes, edges, path, violations);
            checkRequired(nodes, path, violations);
            checkCycle(nodes, edges, path, violations);
        });
        checkCrossPipeline(pipelines, nodesByPipeline, violations);
        checkBodyOutputTypes(normalized, pipelines, violations);
    }

    private static Map<String, Node> parseNodes(Map<String, Object> pipe, String path, List<Violation> violations) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Object> rawNodes = YamlMaps.list(pipe.get("nodes"));
        for (int i = 0; i < rawNodes.size(); i++) {
            Map<String, Object> raw = YamlMaps.map(rawNodes.get(i));
            String id = YamlMaps.stringOrNull(raw.get("id"));
            if (id == null || id.isBlank()) {
                violations.add(new Violation(ValidationCodes.VAL_PIPE_UNKNOWN_NODE, path + "/nodes/" + i, "pipeline node id is required"));
                continue;
            }
            String type = YamlMaps.stringOrNull(raw.get("type"));
            Map<String, Port> ports = parsePorts(raw, type);
            nodes.put(id, new Node(id, type, ports, YamlMaps.map(raw.get("config"))));
        }
        return nodes;
    }

    private static Map<String, Port> parsePorts(Map<String, Object> raw, String type) {
        Map<String, Port> ports = new LinkedHashMap<>();
        Map<String, Object> declared = YamlMaps.map(raw.get("ports"));
        if (!declared.isEmpty()) {
            declared.forEach((key, value) -> {
                Map<String, Object> port = YamlMaps.map(value);
                String name = YamlMaps.stringOrNull(port.get("name"));
                ports.put(key, new Port(
                        key,
                        name == null ? key : name,
                        String.valueOf(port.getOrDefault("type", defaultType(type, key))).toLowerCase(Locale.ROOT),
                        YamlMaps.bool(port.get("required"), false)
                ));
            });
            return ports;
        }
        return defaultPorts(type);
    }

    private static Map<String, Port> defaultPorts(String type) {
        Map<String, Port> ports = new LinkedHashMap<>();
        switch (type == null ? "" : type) {
            case "passthrough" -> {
                ports.put("in", new Port("in", "in", "bytes", false));
                ports.put("out", new Port("out", "out", "bytes", false));
            }
            case "codec.json" -> {
                ports.put("in", new Port("in", "object", "object", true));
                ports.put("out", new Port("out", "body", "bytes", false));
            }
            case "signer.hmac-sha256" -> {
                ports.put("in", new Port("in", "in", "bytes", true));
                ports.put("key", new Port("key", "key", "secret", true));
                ports.put("out", new Port("out", "hex", "string", false));
            }
            case "canonicalizer.concat" -> {
                ports.put("in_secret", new Port("in_secret", "in_secret", "secret", true));
                ports.put("in_nonce", new Port("in_nonce", "in_nonce", "string", true));
                ports.put("in_ts", new Port("in_ts", "in_ts", "number", true));
                ports.put("out", new Port("out", "out", "bytes", false));
            }
            case "canonicalizer.sorted-query" -> {
                ports.put("in", new Port("in", "query", "object", true));
                ports.put("out", new Port("out", "canonical", "bytes", false));
            }
            default -> {
            }
        }
        return ports;
    }

    private static String defaultType(String nodeType, String portKey) {
        Map<String, Port> defaults = defaultPorts(nodeType);
        Port port = defaults.get(portKey);
        return port == null ? "bytes" : port.type;
    }

    private static List<Edge> parseEdges(List<Object> rawEdges, String path, Map<String, Node> nodes, List<Violation> violations) {
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < rawEdges.size(); i++) {
            Map<String, Object> raw = YamlMaps.map(rawEdges.get(i));
            Object from = raw.get("from");
            String toRef = YamlMaps.stringOrNull(raw.get("to"));
            Endpoint to = endpoint(toRef);
            if (to == null) {
                continue;
            }
            if (from instanceof Map<?, ?>) {
                Map<String, Object> fromMap = YamlMaps.map(from);
                String sourceType = fromMap.containsKey("secretRef") ? "secret" : "string";
                edges.add(new Edge(null, null, sourceType, to.nodeId, to.portKey, true));
            } else {
                Endpoint fromEp = endpoint(YamlMaps.stringOrNull(from));
                if (fromEp == null) {
                    continue;
                }
                Node fromNode = nodes.get(fromEp.nodeId);
                if (fromNode == null) {
                    violations.add(new Violation(
                            ValidationCodes.VAL_PIPE_TYPE,
                            path + "/edges/" + i,
                            "cross-pipeline or unknown from node " + fromEp.nodeId
                    ));
                    continue;
                }
                Port fromPort = fromNode.port(fromEp.portKey);
                String sourceType = fromPort == null ? "bytes" : fromPort.type;
                edges.add(new Edge(fromEp.nodeId, fromEp.portKey, sourceType, to.nodeId, to.portKey, false));
            }
        }
        return edges;
    }

    private static void checkUnknown(Map<String, Node> nodes, String path, List<Violation> violations) {
        for (Node node : nodes.values()) {
            if (node.type == null || !KNOWN.contains(node.type)) {
                violations.add(new Violation(
                        ValidationCodes.VAL_PIPE_UNKNOWN_NODE,
                        path + "/nodes/" + node.id,
                        "unknown pipeline node type " + node.type
                ));
            }
        }
    }

    private static void markConfigBindings(Map<String, Node> nodes) {
        for (Node node : nodes.values()) {
            if ("codec.json".equals(node.type) || "passthrough".equals(node.type)) {
                Port in = node.port("in");
                if (in != null) {
                    in.connected = true;
                }
            }
            if ("canonicalizer.sorted-query".equals(node.type)) {
                if (!YamlMaps.map(node.config.get("params")).isEmpty()) {
                    Port in = node.port("in");
                    if (in != null) {
                        in.connected = true;
                    }
                }
                continue;
            }
            if (!"canonicalizer.concat".equals(node.type)) {
                continue;
            }
            List<Object> parts = YamlMaps.list(node.config.get("parts"));
            List<Port> requiredIns = node.ports.values().stream().filter(p -> p.required && !p.key.startsWith("out")).toList();
            for (int i = 0; i < Math.min(parts.size(), requiredIns.size()); i++) {
                requiredIns.get(i).connected = true;
            }
        }
    }

    private static void checkCardinalityAndTypes(Map<String, Node> nodes, List<Edge> edges, String path, List<Violation> violations) {
        Map<String, Integer> inbound = new HashMap<>();
        for (Edge edge : edges) {
            Node toNode = nodes.get(edge.toNode);
            if (toNode == null) {
                violations.add(new Violation(
                        ValidationCodes.VAL_PIPE_TYPE,
                        path + "/edges",
                        "edge target node not in this pipeline: " + edge.toNode
                ));
                continue;
            }
            Port toPort = toNode.port(edge.toPort);
            if (toPort == null) {
                continue;
            }
            String inboundKey = edge.toNode + "." + toPort.key;
            inbound.merge(inboundKey, 1, Integer::sum);
            if (inbound.get(inboundKey) > 1) {
                violations.add(new Violation(
                        ValidationCodes.VAL_PIPE_CARDINALITY,
                        path + "/nodes/" + edge.toNode + "/ports/" + toPort.key,
                        "single-input port has multiple inbound edges"
                ));
            }
            toPort.connected = true;
            if (!typesCompatible(edge.sourceType, toPort.type)) {
                violations.add(new Violation(
                        ValidationCodes.VAL_PIPE_TYPE,
                        path + "/nodes/" + edge.toNode + "/ports/" + toPort.key,
                        "type " + edge.sourceType + " is not assignable to " + toPort.type
                ));
            }
        }
    }

    private static void checkRequired(Map<String, Node> nodes, String path, List<Violation> violations) {
        for (Node node : nodes.values()) {
            for (Port port : node.ports.values()) {
                if (port.required && !port.connected) {
                    violations.add(new Violation(
                            ValidationCodes.VAL_PIPE_PORT_REQUIRED,
                            path + "/nodes/" + node.id + "/ports/" + port.key,
                            "required port is not connected"
                    ));
                }
            }
        }
    }

    private static void checkCycle(Map<String, Node> nodes, List<Edge> edges, String path, List<Violation> violations) {
        Map<String, List<String>> adj = new HashMap<>();
        nodes.keySet().forEach(id -> adj.put(id, new ArrayList<>()));
        for (Edge edge : edges) {
            if (edge.fromNode != null && adj.containsKey(edge.fromNode) && adj.containsKey(edge.toNode)) {
                adj.get(edge.fromNode).add(edge.toNode);
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : nodes.keySet()) {
            if (cycleDfs(id, adj, visiting, visited)) {
                violations.add(new Violation(ValidationCodes.VAL_PIPE_CYCLE, path + "/edges", "pipeline graph contains a cycle"));
                return;
            }
        }
    }

    private static boolean cycleDfs(String id, Map<String, List<String>> adj, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        for (String next : adj.getOrDefault(id, List.of())) {
            if (cycleDfs(next, adj, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }

    private static void checkCrossPipeline(
            Map<String, Object> pipelines,
            Map<String, Set<String>> nodesByPipeline,
            List<Violation> violations
    ) {
        pipelines.forEach((pipelineId, raw) -> {
            List<Object> edges = YamlMaps.list(YamlMaps.map(raw).get("edges"));
            Set<String> local = nodesByPipeline.getOrDefault(pipelineId, Set.of());
            for (int i = 0; i < edges.size(); i++) {
                Map<String, Object> edge = YamlMaps.map(edges.get(i));
                Endpoint from = endpoint(YamlMaps.stringOrNull(edge.get("from")));
                if (from != null && !local.contains(from.nodeId) && otherPipelineHas(nodesByPipeline, pipelineId, from.nodeId)) {
                    violations.add(new Violation(
                            ValidationCodes.VAL_PIPE_TYPE,
                            "/pipelines/" + pipelineId + "/edges/" + i,
                            "cross-pipeline edge is illegal"
                    ));
                }
            }
        });
    }

    private static boolean otherPipelineHas(Map<String, Set<String>> nodesByPipeline, String current, String nodeId) {
        return nodesByPipeline.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(current))
                .anyMatch(entry -> entry.getValue().contains(nodeId));
    }

    private static void checkBodyOutputTypes(
            Map<String, Object> normalized,
            Map<String, Object> pipelines,
            List<Violation> violations
    ) {
        Map<String, Object> requests = YamlMaps.map(normalized.get("requests"));
        requests.forEach((requestId, raw) -> {
            Map<String, Object> request = YamlMaps.map(raw);
            Map<String, Object> body = YamlMaps.map(request.get("body"));
            String pipelineId = YamlMaps.stringOrNull(body.get("pipeline"));
            if (pipelineId == null) {
                return;
            }
            Map<String, Object> pipe = YamlMaps.map(pipelines.get(pipelineId));
            List<Object> nodes = YamlMaps.list(pipe.get("nodes"));
            if (nodes.isEmpty()) {
                return;
            }
            Map<String, Object> last = YamlMaps.map(nodes.getLast());
            Map<String, Object> ports = YamlMaps.map(last.get("ports"));
            Map<String, Object> out = YamlMaps.map(ports.get("out"));
            String type = YamlMaps.stringOrNull(out.get("type"));
            if (type != null && !"bytes".equalsIgnoreCase(type)) {
                violations.add(new Violation(
                        ValidationCodes.VAL_PIPE_TYPE,
                        "/requests/" + requestId + "/body",
                        "HTTP body pipeline must output bytes"
                ));
            }
        });
    }

    private static boolean typesCompatible(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        String a = from.toLowerCase(Locale.ROOT);
        String b = to.toLowerCase(Locale.ROOT);
        return a.equals(b);
    }

    private static Endpoint endpoint(String ref) {
        if (ref == null || !ref.contains(".")) {
            return null;
        }
        int dot = ref.indexOf('.');
        return new Endpoint(ref.substring(0, dot), ref.substring(dot + 1));
    }

    private record Endpoint(String nodeId, String portKey) {
    }

    private static final class Node {
        private final String id;
        private final String type;
        private final Map<String, Port> ports;
        private final Map<String, Object> config;

        private Node(String id, String type, Map<String, Port> ports, Map<String, Object> config) {
            this.id = id;
            this.type = type;
            this.ports = ports;
            this.config = config;
        }

        private Port port(String keyOrName) {
            Port byKey = ports.get(keyOrName);
            if (byKey != null) {
                return byKey;
            }
            return ports.values().stream()
                    .filter(port -> port.name.equals(keyOrName) || port.key.equals(keyOrName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class Port {
        private final String key;
        private final String name;
        private final String type;
        private final boolean required;
        private boolean connected;

        private Port(String key, String name, String type, boolean required) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.required = required;
        }
    }

    private record Edge(String fromNode, String fromPort, String sourceType, String toNode, String toPort, boolean external) {
    }
}
