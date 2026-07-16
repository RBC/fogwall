package com.rbc.fogwall.config;

import lombok.Data;

/** Binds a single entry under {@code rules.allow[]} (or {@code rules.deny[]}) in fogwall.yml. */
@Data
public class RuleConfig {

    private boolean enabled = true;

    /**
     * Explicit evaluation order. Optional — when omitted, order is inferred from this entry's position within its
     * {@code allow[]}/{@code deny[]} array (0, 100, 200, ... leaving gaps for later insertion). When set, the explicit
     * value always takes precedence over the inferred position.
     */
    private Integer order;

    /** Git operation this entry matches: {@code FETCH}, {@code PUSH}, or {@code BOTH} (default). */
    private String operation = "BOTH";

    /** Provider name to scope this entry to. Omit (or leave blank) to match all providers. */
    private String provider = "";

    /** Repository match criteria — target, value, and type. */
    private MatchConfig match = new MatchConfig();
}
