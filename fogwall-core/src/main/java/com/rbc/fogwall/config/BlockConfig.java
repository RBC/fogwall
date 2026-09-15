package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * A content-block policy: an ordered list of {@link MatchRule}s, each a literal or regex, every match a block.
 *
 * <p>Independent of what is being matched. Commit messages, pushed diffs, and SCM API content each configure their own
 * list; the shape is the same in every case, and the runtime type carries compiled rules rather than the strings its
 * YAML counterpart {@code MatchRuleSettings} binds.
 *
 * <p>Content scanning deliberately uses only the reduced {@link MatchRule} shape — no allow action (an "allow" gate is
 * incoherent against a free-text diff or message body) and no field (there is one target, the text itself). The
 * allow/block and field concerns live on {@link EmailRule}, which content controls do not use.
 */
@Data
@Builder
public class BlockConfig {

    /** The block rules, in configuration order. */
    @Builder.Default
    private List<MatchRule> rules = new ArrayList<>();
}
