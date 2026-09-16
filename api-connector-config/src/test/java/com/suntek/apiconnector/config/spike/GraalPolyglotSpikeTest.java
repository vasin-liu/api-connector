/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.config.spike;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraalPolyglotSpikeTest {

    @Test
    void explicitHostAccessBlocksJavaTypeLookup() {
        try (Context ctx = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowIO(false)
                .build()) {
            assertThatThrownBy(() -> ctx.eval("js", "Java.type('java.io.File')"))
                    .isInstanceOf(PolyglotException.class);
        }
    }

    @Test
    void statementLimitInterruptsTightLoop() {
        ResourceLimits limits = ResourceLimits.newBuilder()
                .statementLimit(50_000, null)
                .build();
        Context ctx = Context.newBuilder("js")
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowIO(false)
                .resourceLimits(limits)
                .build();
        try {
            assertThatThrownBy(() -> ctx.eval("js", "var i=0; while(true){ i=i+1; }"))
                    .isInstanceOf(PolyglotException.class)
                    .hasMessageContaining("Statement count limit");
        } finally {
            try {
                ctx.close(true);
            } catch (PolyglotException ignored) {
                // cancelled eval can surface again on close
            }
        }
        assertThat(limits).isNotNull();
    }
}
