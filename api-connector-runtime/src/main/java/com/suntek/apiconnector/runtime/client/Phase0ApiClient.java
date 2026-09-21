/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.client;

import com.suntek.apiconnector.core.api.ApiClient;
import com.suntek.apiconnector.core.api.ExecuteCommand;
import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.api.ExecutionHandle;
import com.suntek.apiconnector.core.api.ExecutionResult;
import com.suntek.apiconnector.core.api.ExecutionSnapshot;
import com.suntek.apiconnector.runtime.flow.ExecutionCancellation;
import com.suntek.apiconnector.runtime.flow.LinearFlowExecutor;
import com.suntek.apiconnector.runtime.plan.CompiledCredential;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.plan.PlanCapability;
import com.suntek.apiconnector.runtime.registry.DefinitionLifecycle;
import com.suntek.apiconnector.runtime.registry.InMemoryDefinitionRegistry;
import com.suntek.apiconnector.runtime.secret.CredentialResolver;
import com.suntek.apiconnector.runtime.secret.MapSecretProvider;
import com.suntek.apiconnector.runtime.session.SessionCoordinator;
import com.suntek.apiconnector.runtime.time.NonceSource;
import com.suntek.apiconnector.transport.HttpTransport;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 0a host: in-memory PUBLISHED registry, plan cache, cancel, capability gate.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class Phase0ApiClient implements ApiClient {

    private static final Set<PlanCapability> IMPLEMENTED = Set.of(
            PlanCapability.LINEAR_FLOW,
            PlanCapability.AUTH_FLOW,
            PlanCapability.SESSION,
            PlanCapability.REPLAY,
            PlanCapability.PIPELINE_GRAPH
    );

    private final InMemoryDefinitionRegistry registry;
    private final HttpTransport transport;
    private final SessionCoordinator sessions;
    private final Clock clock;
    private final NonceSource nonce;
    private final MapSecretProvider memorySecrets;
    private final CredentialResolver secrets;
    private final ConcurrentHashMap<String, ExecutionCancellation> inflight = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "api-connector-exec");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Capability-only client; linear execute requires a transport.
     */
    public Phase0ApiClient() {
        this((request, context) -> {
            throw new IllegalStateException("no transport");
        });
    }

    /**
     * @param transport outbound HTTP
     */
    public Phase0ApiClient(HttpTransport transport) {
        this(new InMemoryDefinitionRegistry(), transport);
    }

    /**
     * @param transport outbound HTTP
     * @param clock     injectable clock for {@code now: epochMillis} / {@code now: isoOffset}
     */
    public Phase0ApiClient(HttpTransport transport, Clock clock) {
        this(new InMemoryDefinitionRegistry(), transport, new SessionCoordinator(), clock);
    }

    /**
     * @param transport outbound HTTP
     * @param clock     injectable clock
     * @param nonce     injectable nonce source for {@code generate: nonce}
     */
    public Phase0ApiClient(HttpTransport transport, Clock clock, NonceSource nonce) {
        this(new InMemoryDefinitionRegistry(), transport, new SessionCoordinator(), clock, nonce);
    }

    /**
     * @param registry  definition registry
     * @param transport outbound HTTP
     */
    public Phase0ApiClient(InMemoryDefinitionRegistry registry, HttpTransport transport) {
        this(registry, transport, new SessionCoordinator());
    }

    /**
     * @param registry  definition registry
     * @param transport outbound HTTP
     * @param sessions  session coordinator
     */
    public Phase0ApiClient(
            InMemoryDefinitionRegistry registry,
            HttpTransport transport,
            SessionCoordinator sessions
    ) {
        this(registry, transport, sessions, Clock.systemUTC());
    }

    /**
     * @param registry  definition registry
     * @param transport outbound HTTP
     * @param sessions  session coordinator
     * @param clock     injectable clock for {@code now: epochMillis} / {@code now: isoOffset}
     */
    public Phase0ApiClient(
            InMemoryDefinitionRegistry registry,
            HttpTransport transport,
            SessionCoordinator sessions,
            Clock clock
    ) {
        this(registry, transport, sessions, clock, NonceSource.uuid());
    }

    /**
     * @param registry  definition registry
     * @param transport outbound HTTP
     * @param sessions  session coordinator
     * @param clock     injectable clock
     * @param nonce     injectable nonce source
     */
    public Phase0ApiClient(
            InMemoryDefinitionRegistry registry,
            HttpTransport transport,
            SessionCoordinator sessions,
            Clock clock,
            NonceSource nonce
    ) {
        this.registry = registry;
        this.transport = transport;
        this.sessions = sessions;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nonce = nonce == null ? NonceSource.uuid() : nonce;
        this.memorySecrets = new MapSecretProvider();
        this.secrets = CredentialResolver.phase0(memorySecrets);
    }

    /**
     * @return mutable in-memory secrets (tests may overwrite fixture identity values)
     */
    public MapSecretProvider secrets() {
        return memorySecrets;
    }

    /**
     * @return session coordinator
     */
    public SessionCoordinator sessions() {
        return sessions;
    }

    /**
     * @return the registry used by this client
     */
    public InMemoryDefinitionRegistry registry() {
        return registry;
    }

    /**
     * Registers an already compiled plan as published.
     *
     * @param plan compiled plan
     */
    public void registerPublished(ExecutionPlan plan) {
        registry.registerPublished(plan);
    }

    /**
     * Loads YAML as published (default lifecycle).
     *
     * @param yaml Canonical Definition YAML
     * @return compiled plan
     */
    public ExecutionPlan loadPublished(String yaml) {
        ExecutionPlan plan = registry.load(yaml);
        seedPlanSecrets(plan);
        return plan;
    }

    /**
     * @param yaml       Canonical Definition YAML
     * @param lifecycle  explicit lifecycle
     * @return compiled plan
     */
    public ExecutionPlan load(String yaml, DefinitionLifecycle lifecycle) {
        return registry.load(yaml, lifecycle);
    }

    @Override
    public ExecutionHandle execute(ExecuteCommand command) {
        ExecuteInputValidator.validate(command);
        ExecutionPlan plan = registry.requirePublished(command.apiId(), command.revision());
        EnumSet<PlanCapability> unsupported = EnumSet.copyOf(plan.capabilities());
        unsupported.removeAll(IMPLEMENTED);
        if (!unsupported.isEmpty()) {
            throw new ExecuteException(
                    ExecuteException.PLAN_CAPABILITY_UNSUPPORTED,
                    "runtime does not execute " + unsupported
            );
        }
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                UUID.randomUUID().toString(),
                plan.definitionId(),
                plan.definitionRevision(),
                plan.planId(),
                Instant.now()
        );
        ExecutionCancellation cancellation = new ExecutionCancellation();
        inflight.put(snapshot.executionId(), cancellation);
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return LinearFlowExecutor.execute(
                        plan,
                        transport,
                        command.input(),
                        cancellation,
                        sessions,
                        clock,
                        secrets,
                        snapshot.executionId(),
                        nonce);
            } finally {
                inflight.remove(snapshot.executionId());
            }
        }, workers);
        return new CompletedExecutionHandle(snapshot, future);
    }

    @Override
    public void cancel(String executionId) {
        ExecutionCancellation cancellation = inflight.get(executionId);
        if (cancellation != null) {
            cancellation.cancel();
        }
    }

    private void seedPlanSecrets(ExecutionPlan plan) {
        if (plan == null || plan.credentials() == null) {
            return;
        }
        for (CompiledCredential credential : plan.credentials().values()) {
            credential.valueRef().ifPresent(memorySecrets::putRefAsMaterialIfAbsent);
            credential.usernameRef().ifPresent(memorySecrets::putRefAsMaterialIfAbsent);
            credential.passwordRef().ifPresent(memorySecrets::putRefAsMaterialIfAbsent);
        }
    }
}
