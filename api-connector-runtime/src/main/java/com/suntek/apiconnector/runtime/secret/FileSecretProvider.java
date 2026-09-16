/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.runtime.secret;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves {@code file:} refs or files under {@code API_CONNECTOR_SECRETS_DIR}.
 *
 * @author Gensokyo
 * @since 2026-09-15
 */
public final class FileSecretProvider implements SecretProvider {

    private final Path directory;

    /**
     * Uses {@code API_CONNECTOR_SECRETS_DIR} when set.
     */
    public FileSecretProvider() {
        String dir = System.getenv("API_CONNECTOR_SECRETS_DIR");
        this.directory = dir == null || dir.isBlank() ? null : Path.of(dir);
    }

    /**
     * @param directory optional secrets root
     */
    public FileSecretProvider(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<byte[]> get(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return Optional.empty();
        }
        try {
            if (secretRef.startsWith("file:")) {
                Path path = Path.of(secretRef.substring("file:".length()));
                return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
            }
            if (directory == null) {
                return Optional.empty();
            }
            Path path = directory.resolve(secretRef).normalize();
            if (!path.startsWith(directory.toAbsolutePath().normalize()) && !path.startsWith(directory.normalize())) {
                return Optional.empty();
            }
            return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
