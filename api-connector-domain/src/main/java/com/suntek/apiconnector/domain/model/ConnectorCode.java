/*
 * Copyright © 2021 - 2026 PCI Technology Group Co.,Ltd. All Rights Reserved.
 * Site: https://www.pcitech.com/
 * Address: PCI Intelligent Building, No.2 Xincen Fourth Road, Tianhe District, Guangzhou, China (Zip code: 510653)
 */
package com.suntek.apiconnector.domain.model;

import java.util.Objects;

/**
 * Third-party connector business code (maps to configuration center code3rd).
 *
 * @author Gensokyo
 * @version 1.0.0
 * @since 2026-06-03
 */
public final class ConnectorCode {

    private final String value;

    /**
     * Creates a connector code with validation.
     *
     * @param value code3rd value
     */
    public ConnectorCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("code3rd must not be blank");
        }
        this.value = value;
    }

    /**
     * Returns the code3rd value.
     *
     * @return code3rd
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectorCode)) {
            return false;
        }
        ConnectorCode that = (ConnectorCode) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ConnectorCode{value='" + value + "'}";
    }
}
