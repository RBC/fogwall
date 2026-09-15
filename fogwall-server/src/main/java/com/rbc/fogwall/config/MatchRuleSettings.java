package com.rbc.fogwall.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw YAML DTO for one content block rule: {@code match} (literal | regex, default regex) and {@code value}. Compiled
 * into a core {@link MatchRule} by {@link JettyConfigurationBuilder}.
 *
 * <p>A rule is written either as a mapping ({@code { match: literal, value: "..." }}) or, since {@code match} defaults
 * to regex and there is nothing else to carry, as a bare scalar string that is taken as a regex. The scalar form is
 * handled two ways because two frameworks bind this list independently: {@link #fromString} covers the Jackson
 * structural validator, and {@link MatchRuleSettingsDecoder} covers Gestalt at runtime.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRuleSettings {

    /** {@code literal} or {@code regex} (default {@code regex}). */
    private String match = "regex";

    /** The literal string or regex source. */
    private String value = "";

    /** A bare scalar entry in the {@code rules} list is a regex over that string. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MatchRuleSettings fromString(String value) {
        return new MatchRuleSettings("regex", value);
    }
}
