package com.rbc.fogwall.validation;

import com.rbc.fogwall.config.ContentPatternConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a {@link ContentPatternConfig}'s configured bundle names against fogwall's built-in bundles, expanding any
 * group aliases (see {@link BuiltInPatternBundleSource#expandAlias}) along the way.
 */
public final class ContentPatternBundleResolver {

    private ContentPatternBundleResolver() {}

    /** @return built-in bundles named/aliased in {@code config.getBundles()}; empty if disabled or nothing selected */
    public static List<PatternBundle> resolve(ContentPatternConfig config) {
        if (!config.isEnabled() || config.getBundles().isEmpty()) {
            return List.of();
        }
        Set<String> selected = config.getBundles().stream()
                .flatMap(name -> BuiltInPatternBundleSource.expandAlias(name).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return BuiltInPatternBundleSource.loadAll().stream()
                .filter(b -> selected.contains(b.name()))
                .collect(Collectors.toList());
    }
}
