/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.session;

import java.util.concurrent.CompletableFuture;

/**
 * Lease from {@link SessionCoordinator#acquire(com.suntek.apiconnector.core.session.SessionLookupKey)}.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class AuthLease {

    /**
     * How this caller participates in refresh.
     */
    public enum Role {
        OWNER,
        WAITER,
        COOLDOWN
    }

    private final Role role;
    private final CompletableFuture<AuthOutcome> future;
    private final AuthOutcome cooldownOutcome;

    private AuthLease(Role role, CompletableFuture<AuthOutcome> future, AuthOutcome cooldownOutcome) {
        this.role = role;
        this.future = future;
        this.cooldownOutcome = cooldownOutcome;
    }

    /**
     * @param future in-flight authentication
     * @return owner lease
     */
    public static AuthLease owner(CompletableFuture<AuthOutcome> future) {
        return new AuthLease(Role.OWNER, future, null);
    }

    /**
     * @param future in-flight authentication
     * @return waiter lease
     */
    public static AuthLease waiter(CompletableFuture<AuthOutcome> future) {
        return new AuthLease(Role.WAITER, future, null);
    }

    /**
     * @param outcome shared cooldown failure
     * @return cooldown lease
     */
    public static AuthLease cooldown(AuthOutcome outcome) {
        return new AuthLease(Role.COOLDOWN, CompletableFuture.completedFuture(outcome), outcome);
    }

    /**
     * @return role
     */
    public Role role() {
        return role;
    }

    /**
     * @return completion of the owner's authentication
     */
    public CompletableFuture<AuthOutcome> future() {
        return future;
    }

    /**
     * @return cooldown outcome when {@link Role#COOLDOWN}
     */
    public AuthOutcome cooldownOutcome() {
        return cooldownOutcome;
    }
}
