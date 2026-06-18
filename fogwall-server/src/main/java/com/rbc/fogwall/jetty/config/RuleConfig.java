package com.rbc.fogwall.jetty.config;

import lombok.Data;

/** Binds a single entry under {@code rules.allow[]} (or {@code rules.deny[]}) in fogwall.yml. */
@Data
public class RuleConfig {

    private boolean enabled = true;
    private int order = 1100;

    /** Git operations this entry matches: {@code FETCH}, {@code PUSH}, or {@code BOTH} (default). */
    private String operations = "BOTH";

    /** Provider name to scope this entry to. Omit (or leave blank) to match all providers. */
    private String provider = "";

    /** Repository match criteria — target, value, and type. */
    private MatchConfig match = new MatchConfig();
}
