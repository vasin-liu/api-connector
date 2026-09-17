/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.config.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Value;

/**
 * GraalVM Polyglot capability host. Scripts get EXPLICIT host access, no IO, and statement limits.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class CapabilityContext implements AutoCloseable {

    private final Context context;

    /**
     * @param language language id (tests use {@code js})
     * @param statementLimit resource statement budget
     */
    public CapabilityContext(String language, long statementLimit) {
        ResourceLimits limits = ResourceLimits.newBuilder()
                .statementLimit(statementLimit, null)
                .build();
        this.context = Context.newBuilder(language)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowIO(false)
                .resourceLimits(limits)
                .build();
    }

    /**
     * JavaScript context with a tight statement budget.
     *
     * @return context
     */
    public static CapabilityContext javascript() {
        return new CapabilityContext("js", 50_000);
    }

    /**
     * @param source script source
     * @return eval result
     */
    public Value eval(String source) {
        return context.eval("js", source);
    }

    @Override
    public void close() {
        context.close(true);
    }
}
