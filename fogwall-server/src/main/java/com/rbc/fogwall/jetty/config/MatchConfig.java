package com.rbc.fogwall.jetty.config;

import lombok.Data;

/** Binds the {@code match:} block shared by rule and permission config entries. */
@Data
public class MatchConfig {

    /** Which part of the repository URL to match against. One of: {@code SLUG}, {@code OWNER}, {@code NAME}. */
    private String target = "SLUG";

    /** Pattern string — interpreted according to {@link #type}. */
    private String value = "";

    /**
     * How {@link #value} is matched. One of: {@code LITERAL}, {@code GLOB}, {@code REGEX}.
     *
     * <p>When omitted, defaults to {@code GLOB} for both URL rules and permissions.
     */
    private String type = null;
}
