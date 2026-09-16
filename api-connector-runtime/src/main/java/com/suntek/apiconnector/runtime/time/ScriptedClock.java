/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test clock that yields scripted epoch milliseconds.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class ScriptedClock extends Clock {

    private final long[] millis;
    private final AtomicInteger index = new AtomicInteger();
    private final ZoneId zone;

    /**
     * @param millis successive {@link Clock#millis()} values
     */
    public ScriptedClock(long... millis) {
        this(ZoneId.of("UTC"), millis);
    }

    /**
     * @param zone   zone
     * @param millis successive values
     */
    public ScriptedClock(ZoneId zone, long... millis) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.millis = millis == null || millis.length == 0 ? new long[] {0L} : millis.clone();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ScriptedClock(zone, millis);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
        int i = Math.min(index.getAndIncrement(), millis.length - 1);
        return millis[i];
    }
}
