/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.transport;

import com.suntek.apiconnector.transport.HttpTransport;
import com.suntek.apiconnector.transport.RawHttpRequest;
import com.suntek.apiconnector.transport.RawHttpResponse;
import com.suntek.apiconnector.transport.TransportContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Scripted HTTP transport for Phase 0 tests.
 *
 * @author Gensokyo
 * @since 2026-09-14
 */
public final class FakeTransport implements HttpTransport {

    private final List<RawHttpResponse> scripted = new ArrayList<>();
    private final List<RawHttpRequest> invocations = new ArrayList<>();
    private int cursor;
    private Function<RawHttpRequest, RawHttpResponse> router;

    private volatile int holdInvocationIndex = -1;
    private final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
    private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

    /**
     * @param responses responses in invocation order
     * @return this
     */
    public FakeTransport enqueue(RawHttpResponse... responses) {
        scripted.addAll(List.of(responses));
        return this;
    }

    /**
     * Blocks returning the response for the given 0-based invocation until {@link #releaseHold()}.
     *
     * @param invocationIndex 0-based index
     * @return this
     */
    public FakeTransport holdBeforeReturn(int invocationIndex) {
        this.holdInvocationIndex = invocationIndex;
        return this;
    }

    /**
     * Waits until the held invocation has been recorded.
     *
     * @throws InterruptedException if interrupted
     */
    public void awaitHold() throws InterruptedException {
        if (!held.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("FakeTransport was not held");
        }
    }

    /**
     * Releases a held invocation.
     */
    public void releaseHold() {
        release.countDown();
    }

    /**
     * Routes by request instead of a scripted list. Used for concurrent session tests.
     *
     * @param router request → response
     * @return this
     */
    public FakeTransport route(Function<RawHttpRequest, RawHttpResponse> router) {
        this.router = router;
        return this;
    }

    @Override
    public RawHttpResponse execute(RawHttpRequest request, TransportContext context) {
        Objects.requireNonNull(request, "request");
        int invocationIndex;
        RawHttpResponse response = null;
        synchronized (this) {
            invocations.add(request);
            invocationIndex = invocations.size() - 1;
            if (router == null) {
                if (cursor >= scripted.size()) {
                    throw new IllegalStateException("FakeTransport has no scripted response for invocation " + (cursor + 1));
                }
                response = scripted.get(cursor++);
            }
        }
        if (router != null) {
            response = router.apply(request);
        }
        if (holdInvocationIndex >= 0 && invocationIndex == holdInvocationIndex) {
            held.countDown();
            try {
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("FakeTransport hold was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("FakeTransport hold interrupted", e);
            }
        }
        return response;
    }

    /**
     * @return recorded outbound requests
     */
    public synchronized List<RawHttpRequest> invocations() {
        return List.copyOf(invocations);
    }
}
