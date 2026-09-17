/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.core.session;

/**
 * Recorded session identity. Includes definition revision for traces.
 * Reuse MUST use {@link #lookupKey()} plus {@link #compatibleWith(SessionKey)},
 * never equality of the full key.
 *
 * @param apiId              api / definition id
 * @param definitionRevision revision that last wrote the session
 * @param authProfile        auth profile id
 * @param credentialRef      credential name used for lookup
 * @author Gensokyo
 * @since 2026-09-15
 */
public record SessionKey(
        String apiId,
        String definitionRevision,
        String authProfile,
        String credentialRef
) {

    /**
     * @return lookup key that ignores revision
     */
    public SessionLookupKey lookupKey() {
        return new SessionLookupKey(apiId, authProfile, credentialRef);
    }

    /**
     * Compatibility predicate: reuse iff api id, auth profile, and credential ref match.
     *
     * @param stored previously recorded key
     * @return true when a stored session may be reused for this key
     */
    public boolean compatibleWith(SessionKey stored) {
        return stored != null
                && apiId.equals(stored.apiId)
                && authProfile.equals(stored.authProfile)
                && credentialRef.equals(stored.credentialRef);
    }
}
