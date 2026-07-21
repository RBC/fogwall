package com.rbc.fogwall.validation;

import java.util.List;

/**
 * A named, jurisdiction-scoped set of {@link PatternRule}s (e.g. {@code national-id-ca}). Operators enable bundles by
 * name in config; see {@code ContentPatternConfig}.
 *
 * @param name unique bundle identifier (e.g. {@code national-id-ca}), referenced from config
 * @param jurisdiction ISO-ish jurisdiction code (e.g. {@code CA}), for display purposes only
 * @param rules the rules making up this bundle
 */
public record PatternBundle(String name, String jurisdiction, List<PatternRule> rules) {}
