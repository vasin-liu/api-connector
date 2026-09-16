/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import com.suntek.apiconnector.core.flow.StepOutcomeType;
import com.suntek.apiconnector.core.session.SessionKey;
import com.suntek.apiconnector.core.session.SessionLookupKey;
import com.suntek.apiconnector.core.session.SessionSnapshot;
import com.suntek.apiconnector.core.value.DataValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store with a single refresh owner per lookup key.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class SessionCoordinator {

    private final Clock clock;
    private final ConcurrentHashMap<SessionLookupKey, Slot> slots = new ConcurrentHashMap<>();

    /**
     * System UTC clock.
     */
    public SessionCoordinator() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock injectable clock for cooldown tests
     */
    public SessionCoordinator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param lookup session lookup key
     * @return lease: owner, waiter, or cooldown
     */
    public AuthLease acquire(SessionLookupKey lookup) {
        Objects.requireNonNull(lookup, "lookup");
        Slot slot = slots.computeIfAbsent(lookup, key -> new Slot());
        synchronized (slot.lock) {
            Instant now = clock.instant();
            if (slot.cooldownUntil != null && now.isBefore(slot.cooldownUntil)) {
                return AuthLease.cooldown(AuthOutcome.failed(slot.failureOutcome, slot.generation));
            }
            if (slot.refreshing != null && !slot.refreshing.isDone()) {
                return AuthLease.waiter(slot.refreshing);
            }
            CompletableFuture<AuthOutcome> inFlight = new CompletableFuture<>();
            slot.refreshing = inFlight;
            return AuthLease.owner(inFlight);
        }
    }

    /**
     * Completes a successful authentication. Generation increases by one.
     *
     * @param lookup      lookup key
     * @param recordedKey SessionKey including the revision that wrote materials
     * @param materials   SESSION-scoped values
     * @param cookies     cookie store (copied)
     * @param ttl         optional expiry
     */
    public void completeSuccess(
            SessionLookupKey lookup,
            SessionKey recordedKey,
            Map<String, DataValue> materials,
            CookieStore cookies,
            Optional<Duration> ttl
    ) {
        Slot slot = requireSlot(lookup);
        synchronized (slot.lock) {
            slot.generation += 1;
            slot.status = "VALID";
            slot.recordedKey = recordedKey;
            slot.materials = Map.copyOf(materials);
            slot.cookies = cookies == null ? new CookieStore() : cookies.copy();
            slot.cooldownUntil = null;
            slot.failureOutcome = StepOutcomeType.FAILURE;
            slot.expiresAt = ttl.map(duration -> clock.instant().plus(duration)).orElse(null);
            AuthOutcome outcome = AuthOutcome.succeeded(slot.generation);
            completeInFlight(slot, outcome);
        }
    }

    /**
     * Completes a shared authentication failure and starts cooldown.
     *
     * @param lookup   lookup key
     * @param outcome  shared outcome
     * @param cooldown failure cooldown; empty means no cooldown window
     */
    public void completeFailure(SessionLookupKey lookup, StepOutcomeType outcome, Optional<Duration> cooldown) {
        Slot slot = requireSlot(lookup);
        synchronized (slot.lock) {
            slot.status = "FAILED";
            slot.failureOutcome = outcome == null ? StepOutcomeType.FAILURE : outcome;
            slot.cooldownUntil = cooldown.map(duration -> clock.instant().plus(duration)).orElse(null);
            AuthOutcome shared = AuthOutcome.failed(slot.failureOutcome, slot.generation);
            completeInFlight(slot, shared);
        }
    }

    /**
     * Seeds a VALID session for concurrency tests (expired token still stored).
     *
     * @param recordedKey SessionKey
     * @param generation  current generation
     * @param materials   SESSION values
     */
    public void seedValid(SessionKey recordedKey, long generation, Map<String, DataValue> materials) {
        Objects.requireNonNull(recordedKey, "recordedKey");
        Slot slot = slots.computeIfAbsent(recordedKey.lookupKey(), key -> new Slot());
        synchronized (slot.lock) {
            slot.recordedKey = recordedKey;
            slot.generation = generation;
            slot.status = "VALID";
            slot.materials = Map.copyOf(materials);
            slot.cookies = new CookieStore();
            slot.cooldownUntil = null;
            slot.expiresAt = null;
        }
    }

    /**
     * @param requested current plan's SessionKey
     * @return stored session when the compatibility predicate matches
     */
    public Optional<BoundSession> findReusable(SessionKey requested) {
        Objects.requireNonNull(requested, "requested");
        Slot slot = slots.get(requested.lookupKey());
        if (slot == null) {
            return Optional.empty();
        }
        synchronized (slot.lock) {
            if (slot.recordedKey == null || !requested.compatibleWith(slot.recordedKey)) {
                return Optional.empty();
            }
            if (!"VALID".equals(slot.status)) {
                return Optional.empty();
            }
            return Optional.of(snapshotLocked(slot));
        }
    }

    /**
     * Re-reads the current session after a waiter resumes. MUST be used instead of a pre-wait token snapshot.
     *
     * @param lookup lookup key
     * @return current bound session
     */
    public Optional<BoundSession> reRead(SessionLookupKey lookup) {
        Slot slot = slots.get(lookup);
        if (slot == null) {
            return Optional.empty();
        }
        synchronized (slot.lock) {
            if (slot.recordedKey == null) {
                return Optional.empty();
            }
            return Optional.of(snapshotLocked(slot));
        }
    }

    /**
     * @param lookup lookup key
     * @return live cookie store for the slot (creates the slot)
     */
    public CookieStore cookies(SessionLookupKey lookup) {
        Slot slot = slots.computeIfAbsent(lookup, key -> new Slot());
        synchronized (slot.lock) {
            return slot.cookies;
        }
    }

    /**
     * @param lookup lookup key
     * @return observable snapshot when a session exists
     */
    public Optional<SessionSnapshot> snapshot(SessionLookupKey lookup) {
        return reRead(lookup).map(BoundSession::toSnapshot);
    }

    private Slot requireSlot(SessionLookupKey lookup) {
        Slot slot = slots.get(lookup);
        if (slot == null) {
            throw new IllegalStateException("no session slot for " + lookup);
        }
        return slot;
    }

    private static void completeInFlight(Slot slot, AuthOutcome outcome) {
        CompletableFuture<AuthOutcome> inFlight = slot.refreshing;
        slot.refreshing = null;
        if (inFlight != null && !inFlight.isDone()) {
            inFlight.complete(outcome);
        }
    }

    private static BoundSession snapshotLocked(Slot slot) {
        return new BoundSession(
                slot.recordedKey,
                slot.generation,
                slot.status,
                slot.materials,
                slot.cookies.copy(),
                slot.expiresAt
        );
    }

    /**
     * Bound session materials after lookup or re-read.
     *
     * @param key        recorded SessionKey (includes revision)
     * @param generation generation
     * @param status     VALID / FAILED
     * @param materials  SESSION values
     * @param cookies    cookie store copy
     * @param expiresAt  optional expiry
     */
    public record BoundSession(
            SessionKey key,
            long generation,
            String status,
            Map<String, DataValue> materials,
            CookieStore cookies,
            Instant expiresAt
    ) {
        /**
         * @return host-visible snapshot without materials
         */
        public SessionSnapshot toSnapshot() {
            return new SessionSnapshot(key.apiId(), generation, status, expiresAt);
        }
    }

    private static final class Slot {
        private final Object lock = new Object();
        private SessionKey recordedKey;
        private long generation;
        private String status = "MISSING";
        private Map<String, DataValue> materials = Map.of();
        private CookieStore cookies = new CookieStore();
        private Instant cooldownUntil;
        private Instant expiresAt;
        private StepOutcomeType failureOutcome = StepOutcomeType.FAILURE;
        private CompletableFuture<AuthOutcome> refreshing;
    }
}
