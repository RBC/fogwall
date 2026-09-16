package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The value of a content {@code block} key ({@code diff-scan.block}, {@code commit.message.block},
 * {@code scm-api.block}). Two shapes are accepted:
 *
 * <ul>
 *   <li>the current shape — a list of matchers, each a bare regex string or a {@code { match, value }} mapping;
 *   <li>the deprecated shape — a {@code { literals: [...], patterns: [...] }} object, folded into equivalent literal
 *       and regex matchers with a startup warning ({@link #deprecatedShape} is then {@code true}).
 * </ul>
 *
 * <p>Both shapes resolve to one list of {@link MatchRuleSettings}. The polymorphism is decoded twice, once per config
 * framework: {@link BlockSettingDeserializer} for the Jackson structural validator, {@link BlockSettingDecoder} for
 * Gestalt at runtime.
 */
@Getter
@JsonDeserialize(using = BlockSettingDeserializer.class)
public final class BlockSetting {

    /** The matchers this block resolves to, whichever shape configured them. Every match blocks. */
    private final List<MatchRuleSettings> matchers;

    /** {@code true} when configured via the deprecated {@code { literals, patterns }} object shape. */
    private final boolean deprecatedShape;

    /** Empty, inert default — no matchers configured. */
    public BlockSetting() {
        this(new ArrayList<>(), false);
    }

    public BlockSetting(List<MatchRuleSettings> matchers, boolean deprecatedShape) {
        this.matchers = matchers;
        this.deprecatedShape = deprecatedShape;
    }
}
