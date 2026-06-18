package com.rbc.fogwall.jetty.config;

import lombok.Data;

/**
 * Binds a single entry in the {@code permissions:} list in fogwall.yml.
 *
 * <p>Example:
 *
 * <pre>
 * permissions:
 *   - username: alice
 *     provider: github
 *     match:
 *       target: SLUG
 *       value: /owner/repo
 *       type: LITERAL
 *     operations: PUSH
 * </pre>
 */
@Data
public class PermissionConfig {

    /** Proxy username that this grant applies to. */
    private String username = "";

    /** Provider name (e.g. {@code github}, {@code gitea}). */
    private String provider = "";

    /** Repository match criteria — target, value, and type. */
    private MatchConfig match = new MatchConfig();

    /**
     * Which operations are granted. {@code PUSH}, {@code REVIEW}, {@code PUSH_AND_REVIEW}, or {@code SELF_CERTIFY}
     * (default: {@code PUSH_AND_REVIEW}).
     */
    private String operations = "PUSH_AND_REVIEW";
}
