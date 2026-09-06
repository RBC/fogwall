package com.rbc.fogwall.config;

import lombok.Data;

/** Binds a single entry under {@code proposals.allow[]} (or {@code proposals.deny[]}) in fogwall.yml. */
@Data
public class ProposalsAccessRuleConfig {

    /** Provider name this entry applies to (e.g. "github"). */
    private String provider = "";

    /** Which proposal traffic this entry matches: {@code READ}, {@code MUTATE}, or {@code BOTH} (default). */
    private String operation = "BOTH";
}
