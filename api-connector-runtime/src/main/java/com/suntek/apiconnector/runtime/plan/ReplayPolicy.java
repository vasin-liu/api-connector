/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.plan;

/**
 * Replay policy on a compiled request.
 *
 * @param replayability         safety class
 * @param allowAutomaticReplay  automatic resend allowed by definition
 * @param maxAttempts           max transport attempts for this request
 * @author Gensokyo
 * @since 2026-09-14
 */
public record ReplayPolicy(Replayability replayability, boolean allowAutomaticReplay, int maxAttempts) {
}
