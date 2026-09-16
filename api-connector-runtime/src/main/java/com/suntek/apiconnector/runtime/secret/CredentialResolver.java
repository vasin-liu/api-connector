/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import com.suntek.apiconnector.core.api.ExecuteException;
import com.suntek.apiconnector.core.value.SecretMetadata;
import com.suntek.apiconnector.core.value.SecretValue;
import com.suntek.apiconnector.runtime.plan.CompiledCredential;
import com.suntek.apiconnector.runtime.plan.ExecutionPlan;
import com.suntek.apiconnector.runtime.value.ByteSecret;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves credential refs at execute time. Plans store refs only.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class CredentialResolver {

    private final List<SecretProvider> providers;

    /**
     * @param providers first match wins
     */
    public CredentialResolver(List<SecretProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    /**
     * Map + environment + file. Callers seed the map for fixtures.
     *
     * @param memory in-memory map
     * @return resolver
     */
    public static CredentialResolver phase0(MapSecretProvider memory) {
        return new CredentialResolver(List.of(
                memory,
                new EnvironmentSecretProvider(),
                new FileSecretProvider()
        ));
    }

    /**
     * @param secretRef provider reference
     * @param apiId     owning api id
     * @return secret
     */
    public SecretValue require(String secretRef, String apiId) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new ExecuteException(ExecuteException.SECRET_UNRESOLVABLE, "empty secret ref");
        }
        for (SecretProvider provider : providers) {
            Optional<byte[]> material = provider.get(secretRef);
            if (material.isPresent()) {
                return new ByteSecret(new SecretMetadata(secretRef, apiId), material.get());
            }
        }
        throw new ExecuteException(ExecuteException.SECRET_UNRESOLVABLE, "cannot resolve " + secretRef);
    }

    /**
     * @param plan          compiled plan (refs only)
     * @param credentialName credential id
     * @param field          username, password, or value
     * @return secret
     */
    public SecretValue requireCredential(ExecutionPlan plan, String credentialName, String field) {
        CompiledCredential cred = plan.credentials().get(credentialName);
        if (cred == null) {
            throw new ExecuteException(ExecuteException.SECRET_UNRESOLVABLE, "unknown credential " + credentialName);
        }
        String ref = switch (field == null ? "" : field) {
            case "username" -> cred.usernameRef().orElse("");
            case "password" -> cred.passwordRef().orElse("");
            default -> cred.valueRef().orElse("");
        };
        return require(ref, cred.apiId());
    }
}
