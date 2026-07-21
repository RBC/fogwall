package com.rbc.fogwall.validation;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A single content-pattern rule: a regex identifying a candidate match, an optional list of nearby keywords required to
 * treat a regex match as a real finding, and an optional named structural validator (see {@link PatternValidators}).
 *
 * <p>Weak, structurally-generic patterns (e.g. a bare 3-4 digit CVV) are unusable as a standalone regex - almost any
 * short number matches. Requiring a {@code contextKeyword} within {@code contextWindow} characters of the match, and a
 * structural check (Luhn, placeholder rejection, etc.) where one exists, is what keeps these rules usable. See
 * {@code PROVENANCE.md} in {@code pattern-bundles/} for where these values come from.
 *
 * @param dataType human-readable name of the data this rule detects (e.g. "Social Insurance Number")
 * @param regex the compiled candidate-match pattern
 * @param contextKeywords case-insensitive keywords; at least one must appear within {@code contextWindow} characters of
 *     the match for it to count as a finding. Empty means no context requirement.
 * @param contextWindow number of characters before/after the match to search for a context keyword
 * @param validator additionally applied to the matched text, or {@code null} if the regex + context match alone is
 *     sufficient. Resolved from a name at load time (see {@link PatternValidators}) so an unregistered validator name
 *     fails loudly when the bundle is loaded, not silently during scanning.
 */
public record PatternRule(
        String dataType, Pattern regex, List<String> contextKeywords, int contextWindow, PatternValidator validator) {}
