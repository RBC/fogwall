package com.rbc.fogwall.validation;

import java.util.List;

/**
 * Wire format for a {@code pattern-bundles/*.json} resource, deserialized then converted to a {@link PatternBundle} by
 * {@link BuiltInPatternBundleSource} (compiling {@code regex} and resolving {@code validator} by name).
 */
record PatternBundleJson(String name, String jurisdiction, List<PatternRuleJson> rules) {

    record PatternRuleJson(
            String dataType, String regex, List<String> contextKeywords, int contextWindow, String validator) {}
}
