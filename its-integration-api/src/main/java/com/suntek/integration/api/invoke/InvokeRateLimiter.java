/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 */
package com.suntek.integration.api.invoke;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 code3rd 的滑动窗口限流（进程内，0 表示关闭）。
 */
public class InvokeRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private volatile int maxPerMinute;

    public void configure(int maxPerMinute) {
        this.maxPerMinute = Math.max(0, maxPerMinute);
    }

    public void checkAllowed(String code3rd) {
        int limit = maxPerMinute;
        if (limit <= 0 || code3rd == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> deque = windows.computeIfAbsent(code3rd, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                throw new InvokeRateLimitException(
                        "Rate limit exceeded for connector " + code3rd + ": " + limit + " invokes per minute");
            }
            deque.addLast(now);
        }
    }
}
