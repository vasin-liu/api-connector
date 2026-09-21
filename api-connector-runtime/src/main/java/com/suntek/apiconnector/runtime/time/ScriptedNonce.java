/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.time;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test nonce source that yields scripted values in order.
 *
 * @author Gensokyo
 * @since 2026-09-20
 */
public final class ScriptedNonce implements NonceSource {

    private final String[] values;
    private final AtomicInteger index = new AtomicInteger();

    /**
     * @param values successive {@link #next()} results; last value repeats if exhausted
     */
    public ScriptedNonce(String... values) {
        this.values = values == null || values.length == 0 ? new String[] {"0"} : values.clone();
    }

    @Override
    public String next() {
        int i = Math.min(index.getAndIncrement(), values.length - 1);
        return values[i];
    }
}
