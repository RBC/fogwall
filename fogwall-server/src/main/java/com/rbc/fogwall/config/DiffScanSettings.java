package com.rbc.fogwall.config;

import lombok.Data;

/**
 * Binds the {@code diff-scan:} block in fogwall.yml. Refuses content found in push diff added-lines.
 *
 * <p>This is a push-level check (operates on the aggregate diff across all commits in the push), distinct from the
 * per-commit checks under {@code commit:}.
 */
@Data
public class DiffScanSettings {

    /**
     * Content block: a list of matchers, or the deprecated {@code { literals, patterns }} object. Every match blocks.
     */
    private BlockSetting block = new BlockSetting();
}
