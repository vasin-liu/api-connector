/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.compile;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.flow.condition.Condition;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.runtime.plan.CompiledCredential;
import com.suntek.apiconnector.runtime.plan.CompiledFlow;
import com.suntek.apiconnector.runtime.plan.CompiledPipeline;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.CompiledStep;
import com.suntek.apiconnector.runtime.plan.CompiledTransition;
import com.suntek.apiconnector.runtime.plan.CompiledVariable;
import com.suntek.apiconnector.runtime.plan.SessionCommit;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.ExecutionPolicy;
import com.suntek.apiconnector.runtime.plan.FlowRole;
import com.suntek.apiconnector.runtime.plan.Limits;
import com.suntek.apiconnector.runtime.plan.NamedBinding;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.plan.ReplayPolicy;
import com.suntek.apiconnector.runtime.plan.Replayability;
import com.suntek.apiconnector.runtime.plan.SecretSink;
import com.suntek.apiconnector.runtime.plan.SessionPolicy;
import com.suntek.apiconnector.runtime.plan.StepKind;
import com.suntek.apiconnector.runtime.plan.UrlTemplate;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds {@link ExecutionPlan} from a normalized definition tree.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class PlanAssembler {

    private PlanAssembler() {
    }

    /**
     * @param planId     hashed normalized JSON
     * @param normalized Normalize output
     * @return immutable plan
     */
    public static ExecutionPlan assemble(String planId, Map<String, Object> normalized) {
        Map<String, Object> definition = YamlMaps.map(normalized.get("definition"));
        Map<String, Object> credentials = YamlMaps.map(normalized.get("credentials"));
        Map<String, Object> limitsMap = YamlMaps.map(normalized.get("limits"));
        Map<String, CompiledRequest> requests = compileRequests(YamlMaps.map(normalized.get("requests")));
        Map<String, CompiledPipeline> pipelines = compilePipelines(YamlMaps.map(normalized.get("pipelines")));
        Map<String, Object> flows = YamlMaps.map(normalized.get("flows"));
        Limits limits = compileLimits(limitsMap);
        CompiledFlow business = compileFlow("business", FlowRole.BUSINESS, YamlMaps.map(flows.get("business")), limits, requests);
        Optional<CompiledFlow> authentication = flows.containsKey("authentication")
                ? Optional.of(compileFlow(
                "authentication",
                FlowRole.AUTHENTICATION,
                YamlMaps.map(flows.get("authentication")),
                limits,
                requests
        ))
                : Optional.empty();
        Set<PlanCapability> capabilities = inferCapabilities(normalized, business, authentication);
        return new ExecutionPlan(
                planId,
                String.valueOf(definition.get("id")),
                String.valueOf(definition.get("revision")),
                String.valueOf(definition.get("authProfile")),
                credentialRef(credentials, YamlMaps.map(normalized.get("session"))),
                compileCredentials(credentials),
                compileVariables(YamlMaps.map(normalized.get("variables"))),
                limits,
                sessionPolicy(YamlMaps.map(normalized.get("session"))),
                executionPolicy(YamlMaps.map(normalized.get("policy"))),
                Map.copyOf(requests),
                Map.copyOf(pipelines),
                business,
                authentication,
                Set.copyOf(capabilities)
        );
    }

    private static Map<String, CompiledVariable> compileVariables(Map<String, Object> variables) {
        Map<String, CompiledVariable> out = new LinkedHashMap<>();
        variables.forEach((name, raw) -> {
            Map<String, Object> var = YamlMaps.map(raw);
            VariableScope scope = VariableScope.valueOf(String.valueOf(var.get("scope")).toUpperCase());
            Optional<DataValue> initial = var.containsKey("value")
                    ? Optional.of(scalar(var.get("value")))
                    : Optional.empty();
            out.put(name, new CompiledVariable(
                    name,
                    scope,
                    String.valueOf(var.getOrDefault("type", "string")),
                    initial
            ));
        });
        return Map.copyOf(out);
    }

    private static DataValue scalar(Object raw) {
        if (raw == null) {
            return new DataValue.NullValue();
        }
        if (raw instanceof Boolean b) {
            return new DataValue.BooleanValue(b);
        }
        if (raw instanceof Number n) {
            return new DataValue.NumberValue(n);
        }
        return new DataValue.StringValue(String.valueOf(raw));
    }

    private static String credentialRef(Map<String, Object> credentials, Map<String, Object> session) {
        if (session.get("credentialRef") != null) {
            return String.valueOf(session.get("credentialRef"));
        }
        if (credentials.size() == 1) {
            return credentials.keySet().iterator().next();
        }
        return "";
    }

    private static Map<String, CompiledCredential> compileCredentials(Map<String, Object> credentials) {
        Map<String, CompiledCredential> out = new LinkedHashMap<>();
        credentials.forEach((name, raw) -> {
            Map<String, Object> cred = YamlMaps.map(raw);
            out.put(name, new CompiledCredential(
                    name,
                    String.valueOf(cred.getOrDefault("type", "")),
                    Optional.ofNullable(YamlMaps.stringOrNull(cred.get("usernameRef"))),
                    Optional.ofNullable(YamlMaps.stringOrNull(cred.get("passwordRef"))),
                    Optional.ofNullable(YamlMaps.stringOrNull(cred.get("valueRef"))),
                    String.valueOf(cred.getOrDefault("apiId", ""))
            ));
        });
        return Map.copyOf(out);
    }

    private static ExecutionPolicy executionPolicy(Map<String, Object> policy) {
        if (policy.isEmpty()) {
            return ExecutionPolicy.defaults();
        }
        return new ExecutionPolicy(
                String.valueOf(policy.getOrDefault("onUnknownOutcome", "FAIL")),
                String.valueOf(policy.getOrDefault("onStaleFlowBinding", "FAIL"))
        );
    }

    private static SessionPolicy sessionPolicy(Map<String, Object> session) {
        if (session.isEmpty()) {
            return SessionPolicy.none();
        }
        Optional<java.time.Duration> ttl = session.get("ttl") == null
                ? Optional.empty()
                : Optional.of(DurationParser.parse(String.valueOf(session.get("ttl"))));
        Optional<java.time.Duration> cooldown = session.get("failureCooldown") == null
                ? Optional.empty()
                : Optional.of(DurationParser.parse(String.valueOf(session.get("failureCooldown"))));
        return new SessionPolicy(ttl, cooldown, YamlMaps.bool(session.get("cookies"), false));
    }

    private static Limits compileLimits(Map<String, Object> limits) {
        return new Limits(
                YamlMaps.integer(limits.get("maxAuthAttempts"), 0),
                YamlMaps.integer(limits.get("maxAuthDepth"), 0),
                YamlMaps.integer(limits.get("transitionLimit"), 8),
                DurationParser.parse(String.valueOf(limits.getOrDefault("executionTimeout", "10s")))
        );
    }

    private static Map<String, CompiledRequest> compileRequests(Map<String, Object> requests) {
        Map<String, CompiledRequest> out = new LinkedHashMap<>();
        requests.forEach((id, raw) -> out.put(id, compileRequest(YamlMaps.map(raw))));
        return out;
    }

    private static CompiledRequest compileRequest(Map<String, Object> request) {
        List<NamedBinding> headers = bindings(YamlMaps.list(request.get("headers")), true);
        List<NamedBinding> query = bindings(YamlMaps.list(request.get("query")), false);
        EnumSet<SecretSink> sinks = EnumSet.noneOf(SecretSink.class);
        headers.forEach(b -> b.sink().ifPresent(sinks::add));
        query.forEach(b -> b.sink().ifPresent(sinks::add));
        Map<String, Object> replay = YamlMaps.map(request.get("replay"));
        return new CompiledRequest(
                String.valueOf(request.get("id")),
                String.valueOf(request.get("method")),
                urlTemplate(YamlMaps.list(request.get("url"))),
                headers,
                query,
                Optional.ofNullable(request.get("body")),
                new ReplayPolicy(
                        Replayability.valueOf(String.valueOf(replay.get("replayability"))),
                        YamlMaps.bool(replay.get("allowAutomaticReplay"), false),
                        YamlMaps.integer(replay.get("maxAttempts"), 1)
                ),
                sinks,
                Optional.ofNullable(YamlMaps.stringOrNull(request.get("cookies")))
        );
    }

    private static UrlTemplate urlTemplate(List<Object> parts) {
        List<UrlTemplate.UrlPart> compiled = new ArrayList<>();
        for (Object part : parts) {
            Map<String, Object> map = YamlMaps.map(part);
            if ("VAR".equals(map.get("kind"))) {
                compiled.add(new UrlTemplate.UrlPart.VarRef(
                        VariableScope.valueOf(String.valueOf(map.get("scope"))),
                        String.valueOf(map.get("name"))
                ));
            } else {
                compiled.add(new UrlTemplate.UrlPart.Literal(String.valueOf(map.get("value"))));
            }
        }
        return new UrlTemplate(List.copyOf(compiled));
    }

    private static List<NamedBinding> bindings(List<Object> items, boolean header) {
        List<NamedBinding> out = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> map = YamlMaps.map(item);
            Optional<SecretSink> sink = map.get("sink") == null
                    ? Optional.empty()
                    : Optional.of(SecretSink.valueOf(String.valueOf(map.get("sink")).toUpperCase().replace('-', '_')));
            out.add(new NamedBinding(
                    String.valueOf(map.get("name")),
                    header ? Optional.ofNullable(YamlMaps.stringOrNull(map.get("nameLower"))) : Optional.empty(),
                    Optional.ofNullable(YamlMaps.stringOrNull(map.get("secretRef"))),
                    sink,
                    map.containsKey("value") ? Optional.of(map.get("value")) : Optional.empty(),
                    map
            ));
        }
        return List.copyOf(out);
    }

    private static Map<String, CompiledPipeline> compilePipelines(Map<String, Object> pipelines) {
        Map<String, CompiledPipeline> out = new LinkedHashMap<>();
        pipelines.forEach((id, raw) -> {
            Map<String, Object> pipe = YamlMaps.map(raw);
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Object node : YamlMaps.list(pipe.get("nodes"))) {
                nodes.add(YamlMaps.map(node));
            }
            out.put(id, new CompiledPipeline(id, List.copyOf(nodes), List.copyOf(YamlMaps.list(pipe.get("edges")))));
        });
        return out;
    }

    private static CompiledFlow compileFlow(
            String flowId,
            FlowRole role,
            Map<String, Object> flow,
            Limits limits,
            Map<String, CompiledRequest> requests
    ) {
        List<CompiledStep> steps = new ArrayList<>();
        for (Object stepRaw : YamlMaps.list(flow.get("steps"))) {
            steps.add(compileStep(YamlMaps.map(stepRaw), requests));
        }
        return new CompiledFlow(flowId, role, List.copyOf(steps), limits.transitionLimit());
    }

    private static CompiledStep compileStep(Map<String, Object> step, Map<String, CompiledRequest> requests) {
        Optional<String> requestId = Optional.ofNullable(YamlMaps.stringOrNull(step.get("request")));
        Optional<String> pipelineId = Optional.ofNullable(YamlMaps.stringOrNull(step.get("pipeline")));
        StepKind kind = kind(step);
        int maxAttempts = requestId.map(requests::get)
                .map(r -> r.replay().maxAttempts())
                .orElse(1);
        List<CompiledTransition> transitions = new ArrayList<>();
        for (Object tRaw : YamlMaps.list(step.get("transitions"))) {
            transitions.add(compileTransition(YamlMaps.map(tRaw), maxAttempts));
        }
        EnumSet<StepOutcomeType> extraCommit = EnumSet.noneOf(StepOutcomeType.class);
        if ("CHALLENGE".equals(String.valueOf(step.get("commitOn")))) {
            extraCommit.add(StepOutcomeType.CHALLENGE);
        }
        List<Map<String, Object>> assigns = new ArrayList<>();
        if (step.get("assign") instanceof Map<?, ?> assignMap) {
            assigns.add(YamlMaps.map(assignMap));
        }
        Optional<Map<String, Object>> extract = step.get("extract") instanceof Map<?, ?>
                ? Optional.of(YamlMaps.map(step.get("extract")))
                : Optional.empty();
        Optional<SessionCommit> sessionCommit = Optional.empty();
        if (step.get("onCommit") instanceof Map<?, ?>) {
            Map<String, Object> onCommit = YamlMaps.map(step.get("onCommit"));
            sessionCommit = Optional.of(new SessionCommit(
                    Optional.ofNullable(YamlMaps.stringOrNull(onCommit.get("sessionStatus"))),
                    "increment".equals(String.valueOf(onCommit.get("generation")))
            ));
        }
        Optional<Map<String, String>> pipelineOutput = Optional.empty();
        if (step.get("output") instanceof Map<?, ?>) {
            Map<String, String> mapped = new LinkedHashMap<>();
            YamlMaps.map(step.get("output")).forEach((key, value) -> mapped.put(key, String.valueOf(value)));
            pipelineOutput = Optional.of(Map.copyOf(mapped));
        }
        Optional<Map<String, Object>> stepInput = step.get("input") instanceof Map<?, ?>
                ? Optional.of(YamlMaps.map(step.get("input")))
                : Optional.empty();
        return new CompiledStep(
                YamlMaps.stringOrNull(step.get("id")),
                kind,
                requestId,
                pipelineId,
                List.copyOf(assigns),
                extract,
                List.copyOf(transitions),
                extraCommit,
                sessionCommit,
                pipelineOutput,
                stepInput
        );
    }

    private static StepKind kind(Map<String, Object> step) {
        if (step.get("request") != null) {
            return StepKind.REQUEST;
        }
        if (step.get("assign") != null) {
            return StepKind.ASSIGN;
        }
        if (step.get("extract") != null) {
            return StepKind.EXTRACT;
        }
        if (step.get("pipeline") != null) {
            return StepKind.PIPELINE;
        }
        return StepKind.ASSIGN;
    }

    private static CompiledTransition compileTransition(Map<String, Object> transition, int maxAttempts) {
        Condition condition = ConditionYamlParser.parse(transition.get("when"));
        TransitionAction action = TransitionAction.valueOf(String.valueOf(transition.get("action")));
        Optional<TransitionAction> thenAction = transition.get("then") == null
                ? Optional.empty()
                : Optional.of(TransitionAction.valueOf(String.valueOf(transition.get("then"))));
        Optional<StepOutcomeType> outcome = transition.get("outcome") == null
                ? Optional.empty()
                : Optional.of(StepOutcomeType.valueOf(String.valueOf(transition.get("outcome"))));
        return new CompiledTransition(
                String.valueOf(transition.get("id")),
                condition,
                action,
                thenAction,
                outcome,
                Optional.ofNullable(YamlMaps.stringOrNull(transition.get("from"))),
                maxAttempts,
                Optional.ofNullable(YamlMaps.stringOrNull(transition.get("session")))
        );
    }

    private static Set<PlanCapability> inferCapabilities(
            Map<String, Object> normalized,
            CompiledFlow business,
            Optional<CompiledFlow> authentication
    ) {
        EnumSet<PlanCapability> caps = EnumSet.of(PlanCapability.LINEAR_FLOW);
        if (authentication.isPresent()) {
            caps.add(PlanCapability.AUTH_FLOW);
            caps.add(PlanCapability.SESSION);
        }
        if (normalized.get("session") != null) {
            caps.add(PlanCapability.SESSION);
        }
        walkActions(business, authentication).forEach(action -> {
            switch (action) {
                case AUTHENTICATE, REFRESH_SESSION -> {
                    caps.add(PlanCapability.AUTH_FLOW);
                    caps.add(PlanCapability.REPLAY);
                }
                case REPLAY_REQUEST, RETRY_REQUEST, RETRY_FLOW -> caps.add(PlanCapability.REPLAY);
                default -> {
                }
            }
        });
        Map<String, Object> pipelines = YamlMaps.map(normalized.get("pipelines"));
        pipelines.forEach((id, raw) -> {
            Map<String, Object> pipe = YamlMaps.map(raw);
            if (!YamlMaps.list(pipe.get("edges")).isEmpty()) {
                caps.add(PlanCapability.PIPELINE_GRAPH);
            }
            for (Object node : YamlMaps.list(pipe.get("nodes"))) {
                String type = YamlMaps.stringOrNull(YamlMaps.map(node).get("type"));
                if (type != null && !"passthrough".equals(type) && !"codec.json".equals(type)) {
                    caps.add(PlanCapability.PIPELINE_GRAPH);
                }
                if (type != null && type.startsWith("script")) {
                    caps.add(PlanCapability.SCRIPT);
                }
            }
        });
        return caps;
    }

    private static List<TransitionAction> walkActions(CompiledFlow business, Optional<CompiledFlow> authentication) {
        List<TransitionAction> actions = new ArrayList<>();
        collect(business, actions);
        authentication.ifPresent(flow -> collect(flow, actions));
        return actions;
    }

    private static void collect(CompiledFlow flow, List<TransitionAction> actions) {
        for (CompiledStep step : flow.steps()) {
            for (CompiledTransition transition : step.transitions()) {
                actions.add(transition.action());
                transition.thenAction().ifPresent(actions::add);
            }
        }
    }
}
