package com.rbc.fogwall.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads fogwall's shipped default pattern bundles from classpath resources under {@code pattern-bundles/}. See
 * {@code PROVENANCE.md} in that directory for where the bundled content comes from.
 *
 * <p>Bundle names are enumerated explicitly ({@link #BUNDLE_NAMES}) rather than scanned from the classpath - resource
 * scanning inside a packaged JAR is unreliable, and the set of built-in bundles only changes when this class is changed
 * anyway.
 */
@Slf4j
public final class BuiltInPatternBundleSource {

    /** Names of bundles shipped as classpath resources at {@code pattern-bundles/<name>.json}. */
    static final List<String> BUNDLE_NAMES = List.of(
            "national-id-ca",
            "national-id-us",
            "national-id-gb",
            "national-id-au",
            "national-id-de",
            "national-id-in",
            "national-id-sg",
            "national-id-za",
            "national-id-es",
            "national-id-se",
            "national-id-tr",
            "national-id-fi",
            "national-id-it",
            "national-id-kr",
            "national-id-ng",
            "national-id-ph",
            "national-id-th",
            "generic-iban",
            "generic-credit-card",
            "generic-crypto-wallet",
            "generic-us-bank-routing");

    /** Prefixes that {@link #expandAlias} treats as a group alias rather than a concrete bundle name. */
    private static final String NATIONAL_ID_PREFIX = "national-id-";

    private static final String GENERIC_PREFIX = "generic-";

    /** Group alias expanding to every shipped {@code national-id-*} bundle. */
    public static final String ALIAS_NATIONAL_ID_ALL_GEOS = "national-id-all-geos";

    /** Group alias expanding to every shipped {@code generic-*} bundle. */
    public static final String ALIAS_GENERIC_ALL = "generic-all";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BuiltInPatternBundleSource() {}

    /** @return every built-in bundle, regardless of which ones a given {@code ContentPatternConfig} selects */
    public static List<PatternBundle> loadAll() {
        return BUNDLE_NAMES.stream().map(BuiltInPatternBundleSource::loadBundle).toList();
    }

    /**
     * Expands a configured bundle name into the concrete bundle name(s) it refers to.
     * {@link #ALIAS_NATIONAL_ID_ALL_GEOS} and {@link #ALIAS_GENERIC_ALL} are shorthand for "every bundle with this
     * prefix" - resolved dynamically against {@link #BUNDLE_NAMES} rather than stored as their own bundle file, so
     * there's no separate list to keep in sync as bundles are added.
     *
     * @return the bundle names {@code configuredName} expands to; a single-element list if it's already a concrete
     *     bundle name (including one that doesn't exist - callers are responsible for filtering unknown names)
     */
    public static List<String> expandAlias(String configuredName) {
        if (ALIAS_NATIONAL_ID_ALL_GEOS.equals(configuredName)) {
            return BUNDLE_NAMES.stream()
                    .filter(n -> n.startsWith(NATIONAL_ID_PREFIX))
                    .toList();
        }
        if (ALIAS_GENERIC_ALL.equals(configuredName)) {
            return BUNDLE_NAMES.stream()
                    .filter(n -> n.startsWith(GENERIC_PREFIX))
                    .toList();
        }
        return List.of(configuredName);
    }

    private static PatternBundle loadBundle(String name) {
        String resource = "pattern-bundles/" + name + ".json";
        try (InputStream in = BuiltInPatternBundleSource.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled pattern-bundle resource: " + resource);
            }
            PatternBundleJson json = OBJECT_MAPPER.readValue(in, PatternBundleJson.class);
            return toPatternBundle(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load pattern bundle resource: " + resource, e);
        }
    }

    private static PatternBundle toPatternBundle(PatternBundleJson json) {
        List<PatternRule> rules = json.rules().stream()
                .map(BuiltInPatternBundleSource::toPatternRule)
                .collect(Collectors.toList());
        return new PatternBundle(json.name(), json.jurisdiction(), rules);
    }

    private static PatternRule toPatternRule(PatternBundleJson.PatternRuleJson ruleJson) {
        PatternValidator validator = PatternValidators.byName(ruleJson.validator())
                .orElseGet(() -> {
                    if (ruleJson.validator() != null) {
                        throw new IllegalStateException("Unregistered pattern validator '" + ruleJson.validator()
                                + "' for data type '" + ruleJson.dataType() + "' - check PatternValidators registry");
                    }
                    return null;
                });
        return new PatternRule(
                ruleJson.dataType(),
                Pattern.compile(ruleJson.regex()),
                ruleJson.contextKeywords() == null ? List.of() : ruleJson.contextKeywords(),
                ruleJson.contextWindow(),
                validator);
    }
}
