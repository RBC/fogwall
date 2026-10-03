package com.rbc.fogwall.config;

import lombok.Data;

/** Binds the {@code secrets:} block in fogwall.yml. */
@Data
public class SecretsConfig {

    /**
     * Refuse to start when any sensitive key is set by its value form, from a YAML file or an environment variable,
     * rather than by its {@code -path} file form.
     */
    private boolean requireFileSourcing = false;
}
