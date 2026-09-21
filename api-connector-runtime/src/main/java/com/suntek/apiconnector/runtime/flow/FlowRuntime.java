/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.flow;

import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.flow.TransitionAction;
import com.suntek.apiconnector.core.flow.VariableScope;
import com.suntek.apiconnector.core.flow.condition.ConditionContext;
import com.suntek.apiconnector.core.flow.condition.TransitionEvaluator;
import com.suntek.apiconnector.core.flow.condition.TransitionRule;
import com.suntek.apiconnector.core.http.ResponseBody;
import com.suntek.apiconnector.core.jsonpath.RestrictedJsonPath;
import com.suntek.apiconnector.core.observe.DecisionTrace;
import com.suntek.apiconnector.core.session.SessionKey;
import com.suntek.apiconnector.core.session.SessionLookupKey;
import com.suntek.apiconnector.core.session.SessionSnapshot;
import com.suntek.apiconnector.core.value.DataValue;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.runtime.observe.TraceBuilder;
import com.suntek.apiconnector.runtime.pipeline.PipelineExecutor;
import com.suntek.apiconnector.runtime.plan.CompiledFlow;
import com.suntek.apiconnector.runtime.plan.CompiledPipeline;
import com.suntek.apiconnector.runtime.plan.CompiledRequest;
import com.suntek.apiconnector.runtime.plan.CompiledStep;
import com.suntek.apiconnector.runtime.plan.CompiledTransition;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.OriginalRequestTemplate;
import com.suntek.apiconnector.runtime.plan.Replayability;
import com.suntek.apiconnector.runtime.plan.StepKind;
import com.suntek.apiconnector.runtime.secret.CredentialResolver;
import com.suntek.apiconnector.runtime.secret.MapSecretProvider;
import com.suntek.apiconnector.runtime.secret.SecretSinkPolicy;
import com.suntek.apiconnector.runtime.secret.SinkDestination;
import com.suntek.apiconnector.runtime.session.AuthLease;
import com.suntek.apiconnector.runtime.session.AuthOutcome;
import com.suntek.apiconnector.runtime.session.CookieStore;
import com.suntek.apiconnector.runtime.session.SessionCoordinator;
import com.suntek.apiconnector.runtime.state.DefaultVariableRuntime;
import com.suntek.apiconnector.runtime.state.VariableRuntime;
import com.suntek.apiconnector.runtime.time.NonceSource;
import com.suntek.apiconnector.runtime.value.ByteSecret;
import com.suntek.apiconnector.runtime.yaml.YamlMaps;
import com.suntek.apiconnector.transport.HttpTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import com.suntek.apiconnector.transport.TransportContext;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Linear REQUEST/ASSIGN/EXTRACT plus AUTHENTICATE, pipeline, retry, and template replay.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class FlowRuntime {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private FlowRuntime() {
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION input
     * @param cancellation cancel flag
     * @param sessions     session coordinator
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions
    ) {
        return execute(plan, transport, input, cancellation, sessions, Clock.systemUTC());
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION input
     * @param cancellation cancel flag
     * @param sessions     session coordinator
     * @param clock        injectable clock for {@code now: epochMillis}
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock
    ) {
        return execute(
                plan,
                transport,
                input,
                cancellation,
                sessions,
                clock,
                CredentialResolver.phase0(new MapSecretProvider()),
                java.util.UUID.randomUUID().toString(),
                NonceSource.uuid()
        );
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION input
     * @param cancellation cancel flag
     * @param sessions     session coordinator
     * @param clock        injectable clock
     * @param secrets      credential resolver
     * @param executionId  host execution id
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock,
            CredentialResolver secrets,
            String executionId
    ) {
        return execute(plan, transport, input, cancellation, sessions, clock, secrets, executionId, NonceSource.uuid());
    }

    /**
     * @param plan         compiled plan
     * @param transport    outbound HTTP
     * @param input        EXECUTION input
     * @param cancellation cancel flag
     * @param sessions     session coordinator
     * @param clock        injectable clock
     * @param secrets      credential resolver
     * @param executionId  host execution id
     * @param nonce        injectable nonce source for {@code generate: nonce}
     * @return terminal result
     */
    public static ExecutionResult execute(
            ExecutionPlan plan,
            HttpTransport transport,
            Map<String, DataValue> input,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            Clock clock,
            CredentialResolver secrets,
            String executionId,
            NonceSource nonce
    ) {
        DefaultVariableRuntime variables = new DefaultVariableRuntime(plan.variables());
        seedExecutionInput(variables, input);
        SessionKey requested = plan.sessionKey();
        sessions.findReusable(requested).ifPresent(bound -> variables.bindSession(bound.materials()));
        Frame frame = new Frame(
                plan,
                transport,
                cancellation,
                sessions,
                variables,
                requested,
                clock == null ? Clock.systemUTC() : clock,
                new boolean[] {false},
                secrets == null ? CredentialResolver.phase0(new MapSecretProvider()) : secrets,
                new SecretSinkPolicy(),
                new TraceBuilder(executionId),
                nonce == null ? NonceSource.uuid() : nonce
        );
        ExecutionResult result = runFlow(frame, plan.business(), 0, 0, null);
        Optional<SessionSnapshot> snapshot = sessions.snapshot(requested.lookupKey());
        return new ExecutionResult(result.outcome(), result.httpStatus(), result.body(), frame.trace.snapshot(), snapshot);
    }

    private static ExecutionResult runFlow(
            Frame frame,
            CompiledFlow flow,
            int depth,
            int authAttempts,
            RawHttpResponse seedLast
    ) {
        RawHttpResponse last = seedLast;
        for (CompiledStep step : flow.steps()) {
            if (frame.cancellation.isCancelled()) {
                return cancelled(frame);
            }
            VariableRuntime.StateMutation mutation = frame.variables.beginLocal();
            if (step.kind() == StepKind.ASSIGN) {
                applyAssigns(step, mutation, frame);
                frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
                applySessionCommit(frame, step);
                continue;
            }
            if (step.kind() == StepKind.EXTRACT) {
                applyExtract(step, mutation, last, frame.plan);
                StepOutcomeType outcome = step.extraCommitOn().contains(StepOutcomeType.CHALLENGE)
                        ? StepOutcomeType.CHALLENGE
                        : StepOutcomeType.SUCCESS;
                if (outcome == StepOutcomeType.CHALLENGE) {
                    DefaultVariableRuntime.allowChallengeCommit(mutation);
                }
                frame.variables.commit(mutation, outcome);
                if (outcome == StepOutcomeType.CHALLENGE) {
                    frame.trace.add("CHALLENGE", "BIND", "NONCE_BOUND", Map.of("step", step.stepId()));
                }
                applySessionCommit(frame, step);
                continue;
            }
            if (step.kind() == StepKind.PIPELINE) {
                runPipelineStep(frame, step, mutation);
                continue;
            }
            if (step.kind() != StepKind.REQUEST) {
                frame.variables.discard(mutation);
                continue;
            }
            StepResult stepResult = runRequestStep(frame, step, mutation, depth, authAttempts);
            last = stepResult.last;
            authAttempts = stepResult.authAttempts;
            if (stepResult.terminal.isPresent()) {
                return stepResult.terminal.get();
            }
        }
        return new ExecutionResult(
                StepOutcomeType.SUCCESS,
                last == null ? Optional.empty() : Optional.of(last.status()),
                last == null ? new ResponseBody.EmptyBody() : last.body(),
                frame.trace.snapshot(),
                Optional.empty()
        );
    }

    private static StepResult runRequestStep(
            Frame frame,
            CompiledStep step,
            VariableRuntime.StateMutation mutation,
            int depth,
            int authAttempts
    ) {
        CompiledRequest request = frame.plan.requests().get(step.requestId().orElseThrow());
        OriginalRequestTemplate template = OriginalRequestTemplate.of(request);
        RawHttpResponse last = null;
        int remaining = Math.max(1, frame.plan.limits().transitionLimit());
        int sends = 0;
        while (remaining-- > 0) {
            if (frame.cancellation.isCancelled()) {
                frame.variables.discard(mutation);
                return StepResult.done(cancelled(frame), last, authAttempts);
            }
            CookieStore cookies = frame.sessions.cookies(frame.requested.lookupKey());
            long observedGeneration = frame.sessions.reRead(frame.requested.lookupKey())
                    .map(SessionCoordinator.BoundSession::generation)
                    .orElse(0L);
            Optional<byte[]> body = renderBody(frame, step, request);
            int requestAttempt = frame.trace.nextRequestAttempt();
            RawHttpRequest outbound = RequestRenderer.render(
                    request,
                    frame.variables,
                    cookies,
                    body,
                    frame.sinks,
                    new SinkDestination(frame.plan.definitionId())
            );
            last = frame.transport.execute(outbound, new TransportContext(frame.plan.limits().executionTimeout().toMillis()));
            sends++;
            boolean signed = frame.variables.get(VariableScope.FLOW, "signed")
                    .map(v -> v instanceof DataValue.BooleanValue bv && bv.value())
                    .orElse(false);
            if (signed) {
                frame.nonceConsumed[0] = true;
            }
            if (request.cookies().filter("acceptSetCookie"::equals).isPresent()) {
                List<String> setCookie = headerValues(last.headers(), "Set-Cookie");
                cookies.acceptSetCookie(outbound.uri(), setCookie);
            }
            if (frame.cancellation.isCancelled()) {
                frame.variables.discard(mutation);
                return StepResult.done(cancelled(frame), last, authAttempts);
            }
            if (!last.completed()) {
                frame.variables.discard(mutation);
                return StepResult.done(new ExecutionResult(
                        StepOutcomeType.UNKNOWN_OUTCOME,
                        Optional.empty(),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            if (last.body() instanceof ResponseBody.StreamBody) {
                frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
                return StepResult.done(new ExecutionResult(
                        StepOutcomeType.SUCCESS,
                        Optional.of(last.status()),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            ConditionContext context = ConditionContext.completed(
                    last.status(),
                    last.headers(),
                    last.body(),
                    (scope, name) -> frame.variables.get(scope, name)
            );
            List<TransitionRule> rules = new ArrayList<>();
            step.transitions().forEach(t -> rules.add(new TransitionRule(t.condition(), t.action())));
            Optional<TransitionEvaluator.MatchedTransition> matched = TransitionEvaluator.firstMatch(context, rules);
            if (matched.isEmpty()) {
                StepOutcomeType fallback = fallback(last.status());
                if (fallback == StepOutcomeType.SUCCESS) {
                    frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
                } else {
                    frame.variables.discard(mutation);
                }
                return StepResult.done(new ExecutionResult(
                        fallback,
                        Optional.of(last.status()),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            CompiledTransition hit = step.transitions().get(matched.get().index());
            TransitionAction action = downgrade(request, hit.action());
            frame.trace.add("CONDITION", action.name(), hit.action().name(), Map.of(
                    "status", String.valueOf(last.status()),
                    "requestAttempt", String.valueOf(requestAttempt)
            ));
            if (action != hit.action()) {
                frame.trace.add("POLICY", action.name(), "DOWNGRADE", Map.of("matched", hit.action().name()));
            }
            if (action == TransitionAction.RETRY_REQUEST) {
                if (sends >= request.replay().maxAttempts()) {
                    frame.variables.discard(mutation);
                    return StepResult.done(new ExecutionResult(
                            StepOutcomeType.FAILURE,
                            Optional.of(last.status()),
                            last.body(),
                            frame.trace.snapshot(),
                            Optional.empty()
                    ), last, authAttempts);
                }
                mutation = frame.variables.beginLocal();
                continue;
            }
            if (action == TransitionAction.RETRY_FLOW) {
                frame.variables.clearScope(VariableScope.FLOW);
                frame.nonceConsumed[0] = false;
                AuthThen retryAuth = retryFlow(frame, last, depth, authAttempts);
                authAttempts = retryAuth.authAttempts;
                if (retryAuth.terminal.isPresent()) {
                    return StepResult.done(retryAuth.terminal.get(), last, authAttempts);
                }
                mutation = frame.variables.beginLocal();
                continue;
            }
            if (action == TransitionAction.SUCCESS) {
                frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
                applySessionCommit(frame, step);
                return StepResult.done(new ExecutionResult(
                        StepOutcomeType.SUCCESS,
                        Optional.of(last.status()),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            if (action == TransitionAction.FAIL) {
                frame.variables.discard(mutation);
                StepOutcomeType outcome = hit.outcomeOverride().orElse(StepOutcomeType.FAILURE);
                return StepResult.done(new ExecutionResult(
                        outcome,
                        Optional.of(last.status()),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            if (action == TransitionAction.CONTINUE) {
                frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
                applySessionCommit(frame, step);
                return StepResult.next(last, authAttempts);
            }
            if (action == TransitionAction.AUTHENTICATE || action == TransitionAction.REFRESH_SESSION) {
                frame.trace.add("AUTH_TRIGGER", action.name(), "AUTH_CHALLENGE", Map.of(
                        "status", String.valueOf(last.status())
                ));
                if (step.extraCommitOn().contains(StepOutcomeType.CHALLENGE)) {
                    DefaultVariableRuntime.allowChallengeCommit(mutation);
                    frame.variables.commit(mutation, StepOutcomeType.CHALLENGE);
                } else {
                    frame.variables.discard(mutation);
                }
                mutation = frame.variables.beginLocal();
                Optional<SessionCoordinator.BoundSession> refreshed = frame.sessions.reRead(frame.requested.lookupKey());
                if (refreshed.isPresent()
                        && "VALID".equals(refreshed.get().status())
                        && refreshed.get().generation() > observedGeneration) {
                    rebind(frame);
                    continue;
                }
                if (frame.nonceConsumed[0]
                        && "RETRY_FLOW".equals(frame.plan.executionPolicy().onStaleFlowBinding())
                        && hit.thenAction().orElse(TransitionAction.REPLAY_REQUEST) == TransitionAction.REPLAY_REQUEST) {
                    frame.variables.clearScope(VariableScope.FLOW);
                    frame.nonceConsumed[0] = false;
                    AuthThen retryAuth = retryFlow(frame, last, depth, authAttempts);
                    authAttempts = retryAuth.authAttempts;
                    if (retryAuth.terminal.isPresent()) {
                        return StepResult.done(retryAuth.terminal.get(), last, authAttempts);
                    }
                    mutation = frame.variables.beginLocal();
                    continue;
                }
                AuthThen auth = authenticate(
                        frame,
                        template,
                        hit.thenAction().orElse(TransitionAction.REPLAY_REQUEST),
                        depth,
                        authAttempts,
                        last
                );
                authAttempts = auth.authAttempts;
                if (auth.terminal.isPresent()) {
                    return StepResult.done(auth.terminal.get(), last, authAttempts);
                }
                if (auth.then == TransitionAction.REPLAY_REQUEST) {
                    frame.trace.add("REPLAY", "REPLAY_REQUEST", "TEMPLATE_REBUILD", Map.of(
                            "request", request.requestId()
                    ));
                    continue;
                }
                if (auth.then == TransitionAction.CONTINUE) {
                    return StepResult.next(last, authAttempts);
                }
                return StepResult.done(new ExecutionResult(
                        StepOutcomeType.FAILURE,
                        Optional.of(last.status()),
                        last.body(),
                        frame.trace.snapshot(),
                        Optional.empty()
                ), last, authAttempts);
            }
            frame.variables.discard(mutation);
            throw new IllegalStateException("flow runtime cannot run action " + action);
        }
        frame.variables.discard(mutation);
        return StepResult.done(new ExecutionResult(
                StepOutcomeType.FAILURE,
                last == null ? Optional.empty() : Optional.of(last.status()),
                last == null ? new ResponseBody.EmptyBody() : last.body(),
                frame.trace.snapshot(),
                Optional.empty()
        ), last, authAttempts);
    }

    private static AuthThen authenticate(
            Frame frame,
            OriginalRequestTemplate template,
            TransitionAction then,
            int depth,
            int authAttempts,
            RawHttpResponse challenge
    ) {
        int maxAttempts = frame.plan.limits().maxAuthAttempts();
        int maxDepth = frame.plan.limits().maxAuthDepth();
        if (authAttempts >= maxAttempts || depth >= maxDepth) {
            return new AuthThen(then, authAttempts, Optional.of(new ExecutionResult(
                    StepOutcomeType.AUTH_ATTEMPT_EXCEEDED,
                    Optional.empty(),
                    new ResponseBody.EmptyBody(),
                    frame.trace.snapshot(),
                    Optional.empty()
            )));
        }
        CompiledFlow authentication = frame.plan.authentication().orElse(null);
        if (authentication == null || authentication.steps().isEmpty()) {
            return new AuthThen(then, authAttempts, Optional.of(new ExecutionResult(
                    StepOutcomeType.FAILURE,
                    Optional.empty(),
                    new ResponseBody.EmptyBody(),
                    frame.trace.snapshot(),
                    Optional.empty()
            )));
        }
        SessionLookupKey lookup = frame.requested.lookupKey();
        AuthLease lease = frame.sessions.acquire(lookup);
        if (lease.role() == AuthLease.Role.COOLDOWN) {
            return new AuthThen(then, authAttempts, Optional.of(new ExecutionResult(
                    lease.cooldownOutcome().outcome(),
                    Optional.empty(),
                    new ResponseBody.EmptyBody(),
                    frame.trace.snapshot(),
                    Optional.empty()
            )));
        }
        if (lease.role() == AuthLease.Role.WAITER) {
            AuthOutcome waited = await(lease, frame.plan.limits().executionTimeout());
            rebind(frame);
            if (!waited.success()) {
                return new AuthThen(then, authAttempts, Optional.of(new ExecutionResult(
                        waited.outcome(),
                        Optional.empty(),
                        new ResponseBody.EmptyBody(),
                        frame.trace.snapshot(),
                        Optional.empty()
                )));
            }
            return new AuthThen(then, authAttempts, Optional.empty());
        }
        authAttempts += 1;
        frame.variables.clearScope(VariableScope.FLOW);
        try {
            ExecutionResult authResult = runFlow(frame, authentication, depth + 1, authAttempts, challenge);
            if (authResult.outcome() == StepOutcomeType.CANCELLED) {
                frame.sessions.completeFailure(lookup, StepOutcomeType.CANCELLED, Optional.empty());
                return new AuthThen(then, authAttempts, Optional.of(authResult));
            }
            if (authResult.outcome() != StepOutcomeType.SUCCESS) {
                frame.sessions.completeFailure(
                        lookup,
                        authResult.outcome(),
                        frame.plan.sessionPolicy().failureCooldown()
                );
                return new AuthThen(then, authAttempts, Optional.of(authResult));
            }
            frame.sessions.completeSuccess(
                    lookup,
                    frame.requested,
                    frame.variables.sessionMaterials(),
                    frame.sessions.cookies(lookup),
                    frame.plan.sessionPolicy().ttl()
            );
            frame.trace.add("SESSION", "VALID", "GENERATION_INCREMENT", Map.of());
            rebind(frame);
            return new AuthThen(then, authAttempts, Optional.empty());
        } catch (RuntimeException ex) {
            frame.sessions.completeFailure(lookup, StepOutcomeType.FAILURE, Optional.empty());
            throw ex;
        }
    }

    private static AuthThen retryFlow(Frame frame, RawHttpResponse last, int depth, int authAttempts) {
        return authenticate(frame, new OriginalRequestTemplate(""), TransitionAction.REPLAY_REQUEST, depth, authAttempts, last);
    }

    private static TransitionAction downgrade(CompiledRequest request, TransitionAction action) {
        if (action != TransitionAction.RETRY_REQUEST && action != TransitionAction.REPLAY_REQUEST) {
            return action;
        }
        Replayability replayability = request.replay().replayability();
        if (replayability == Replayability.UNSAFE || replayability == Replayability.UNKNOWN) {
            return TransitionAction.FAIL;
        }
        if (!request.replay().allowAutomaticReplay()) {
            return TransitionAction.FAIL;
        }
        return action;
    }

    private static void runPipelineStep(
            Frame frame,
            CompiledStep step,
            VariableRuntime.StateMutation mutation
    ) {
        String pipelineId = step.pipelineId().orElse(null);
        if (pipelineId == null) {
            frame.variables.discard(mutation);
            return;
        }
        CompiledPipeline pipeline = frame.plan.pipelines().get(pipelineId);
        Map<String, DataValue> ports = PipelineExecutor.execute(
                pipeline, frame.plan, frame.variables, Optional.empty(), frame.secrets, frame.sinks);
        frame.trace.add("PIPELINE", "CONTINUE", "PIPELINE_OK", Map.of("pipeline", pipelineId));
        step.pipelineOutput().ifPresent(mapping -> mapping.forEach((target, portRef) -> {
            int dot = target.indexOf('.');
            if (dot <= 0) {
                return;
            }
            VariableScope scope = VariableScope.valueOf(target.substring(0, dot).toUpperCase());
            String name = target.substring(dot + 1);
            DataValue value = ports.get(portRef);
            if (value != null) {
                mutation.set(scope, name, value);
            }
        }));
        frame.variables.commit(mutation, StepOutcomeType.SUCCESS);
        applySessionCommit(frame, step);
    }

    private static Optional<byte[]> renderBody(Frame frame, CompiledStep step, CompiledRequest request) {
        if (request.body().isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> body = YamlMaps.map(request.body().orElse(null));
        String pipelineId = YamlMaps.stringOrNull(body.get("pipeline"));
        if (pipelineId == null) {
            return Optional.empty();
        }
        CompiledPipeline pipeline = frame.plan.pipelines().get(pipelineId);
        if (pipeline == null) {
            return Optional.empty();
        }
        Map<String, DataValue> fields = new LinkedHashMap<>();
        frame.plan.variables().forEach((name, variable) -> {
            if (variable.scope() == VariableScope.EXECUTION) {
                frame.variables.get(VariableScope.EXECUTION, name).ifPresent(value -> fields.put(name, value));
            }
        });
        DataValue object = new DataValue.ObjectValue(Map.copyOf(fields));
        return Optional.of(PipelineExecutor.encodeJsonBody(
                pipeline, frame.plan, frame.variables, object, frame.secrets, frame.sinks));
    }

    private static AuthOutcome await(AuthLease lease, Duration timeout) {
        try {
            return lease.future().get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return AuthOutcome.failed(StepOutcomeType.FAILURE, 0);
        }
    }

    private static void rebind(Frame frame) {
        frame.sessions.reRead(frame.requested.lookupKey())
                .ifPresent(bound -> frame.variables.bindSession(bound.materials()));
    }

    private static void applySessionCommit(Frame frame, CompiledStep step) {
        step.sessionCommit().ifPresent(commit -> {
            if (commit.sessionStatus().filter("VALID"::equals).isEmpty()) {
                return;
            }
        });
    }

    private static void applyAssigns(CompiledStep step, VariableRuntime.StateMutation mutation, Frame frame) {
        for (Map<String, Object> assign : step.assigns()) {
            assign.forEach((target, raw) -> {
                int dot = target.indexOf('.');
                if (dot <= 0) {
                    return;
                }
                VariableScope scope = VariableScope.valueOf(target.substring(0, dot).toUpperCase());
                String name = target.substring(dot + 1);
                mutation.set(scope, name, resolveValue(raw, frame));
            });
        }
    }

    private static void applyExtract(
            CompiledStep step,
            VariableRuntime.StateMutation mutation,
            RawHttpResponse last,
            ExecutionPlan plan
    ) {
        Map<String, Object> extract = step.extract().orElse(Map.of());
        Map<String, Object> from = YamlMaps.map(extract.get("from"));
        String to = YamlMaps.stringOrNull(extract.get("to"));
        if (to == null || last == null) {
            return;
        }
        int dot = to.indexOf('.');
        if (dot <= 0) {
            return;
        }
        VariableScope scope = VariableScope.valueOf(to.substring(0, dot).toUpperCase());
        String name = to.substring(dot + 1);
        String headerName = YamlMaps.stringOrNull(from.get("header"));
        if (headerName != null) {
            List<String> values = headerValues(last.headers(), headerName);
            if (!values.isEmpty()) {
                mutation.set(scope, name, new DataValue.StringValue(values.getFirst()));
            }
            return;
        }
        String path = YamlMaps.stringOrNull(from.get("jsonpath"));
        if (path == null) {
            return;
        }
        String json = bodyJson(last.body());
        RestrictedJsonPath.JsonSelect selected = RestrictedJsonPath.select(json, path);
        if (!(selected instanceof RestrictedJsonPath.JsonSelect.Found found) || found.value() == null) {
            return;
        }
        boolean asSecret = "secret".equals(YamlMaps.stringOrNull(from.get("as")));
        String text = String.valueOf(found.value());
        DataValue value = asSecret
                ? ByteSecret.utf8(new SecretMetadata(to, plan.definitionId()), text)
                : new DataValue.StringValue(text);
        mutation.set(scope, name, value);
    }

    private static DataValue resolveValue(Object raw, Frame frame) {
        if (raw instanceof Map<?, ?>) {
            Map<String, Object> map = YamlMaps.map(raw);
            if (map.containsKey("object")) {
                Map<String, DataValue> fields = new LinkedHashMap<>();
                YamlMaps.map(map.get("object")).forEach((key, value) -> fields.put(key, resolveValue(value, frame)));
                return new DataValue.ObjectValue(Map.copyOf(fields));
            }
            if (map.containsKey("credential")) {
                return resolveCredential(String.valueOf(map.get("credential")), frame);
            }
            if (map.containsKey("now")) {
                String now = String.valueOf(map.get("now"));
                if ("epochMillis".equals(now)) {
                    return new DataValue.NumberValue(frame.clock.millis());
                }
                if ("isoOffset".equals(now)) {
                    return new DataValue.StringValue(
                            ZonedDateTime.ofInstant(frame.clock.instant(), frame.clock.getZone()).format(ISO_OFFSET));
                }
                throw new IllegalArgumentException("now must be epochMillis or isoOffset");
            }
            if (map.containsKey("generate")) {
                if (!"nonce".equals(String.valueOf(map.get("generate")))) {
                    throw new IllegalArgumentException("generate must be nonce");
                }
                return new DataValue.StringValue(frame.nonce.next());
            }
        }
        if (raw instanceof Boolean b) {
            return new DataValue.BooleanValue(b);
        }
        if (raw instanceof Number n) {
            return new DataValue.NumberValue(n);
        }
        if (raw == null) {
            return new DataValue.NullValue();
        }
        return new DataValue.StringValue(String.valueOf(raw));
    }

    private static DataValue resolveCredential(String path, Frame frame) {
        int dot = path.indexOf('.');
        String credName = dot < 0 ? path : path.substring(0, dot);
        String field = dot < 0 ? "" : path.substring(dot + 1);
        return frame.secrets.requireCredential(frame.plan, credName, field);
    }

    private static String bodyJson(ResponseBody body) {
        if (body instanceof ResponseBody.BytesBody bytes) {
            return new String(bytes.bytes(), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return List.of();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? List.of() : entry.getValue();
            }
        }
        return List.of();
    }

    private static void seedExecutionInput(DefaultVariableRuntime variables, Map<String, DataValue> input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        VariableRuntime.StateMutation mutation = variables.beginLocal();
        input.forEach((key, value) -> {
            String name = key;
            int dot = key.indexOf('.');
            if (dot > 0) {
                name = key.substring(dot + 1);
            }
            mutation.set(VariableScope.EXECUTION, name, value);
        });
        variables.commit(mutation, StepOutcomeType.SUCCESS);
    }

    private static StepOutcomeType fallback(int status) {
        if (status >= 200 && status < 300) {
            return StepOutcomeType.SUCCESS;
        }
        return StepOutcomeType.FAILURE;
    }

    private static ExecutionResult cancelled(Frame frame) {
        return new ExecutionResult(
                StepOutcomeType.CANCELLED,
                Optional.empty(),
                new ResponseBody.EmptyBody(),
                frame.trace.snapshot(),
                Optional.empty()
        );
    }

    private record Frame(
            ExecutionPlan plan,
            HttpTransport transport,
            ExecutionCancellation cancellation,
            SessionCoordinator sessions,
            DefaultVariableRuntime variables,
            SessionKey requested,
            Clock clock,
            boolean[] nonceConsumed,
            CredentialResolver secrets,
            SecretSinkPolicy sinks,
            TraceBuilder trace,
            NonceSource nonce
    ) {
    }

    private record StepResult(Optional<ExecutionResult> terminal, RawHttpResponse last, int authAttempts) {
        static StepResult done(ExecutionResult result, RawHttpResponse last, int authAttempts) {
            return new StepResult(Optional.of(result), last, authAttempts);
        }

        static StepResult next(RawHttpResponse last, int authAttempts) {
            return new StepResult(Optional.empty(), last, authAttempts);
        }
    }

    private record AuthThen(TransitionAction then, int authAttempts, Optional<ExecutionResult> terminal) {
    }
}
