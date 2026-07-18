package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Binds the {@code binary-blob:} block in fogwall.yml. Controls detection of binary blobs in pushed commits by size and
 * content-sniffed MIME type.
 *
 * <p>This is a push-level check (operates on the aggregate diff across all commits in the push), mirroring
 * {@link DiffScanSettings}.
 *
 * <p>Defaults to fully inert (disabled, no size limit, no denied MIME types) — same posture as
 * {@link SecretScanSettings#isEnabled()} and {@link DiffScanSettings}'s empty block lists. The shipped
 * {@code fogwall.yml} explicitly enables this check with recommended values; this class's own defaults are the safe
 * fallback for configs that omit the {@code binary-blob:} key entirely (e.g. minimal hand-written or embedded configs).
 */
@Data
public class BinaryBlobSettings {
    private boolean enabled = false;
    private long maxSizeBytes = 0;
    private List<String> denyMimeTypes = new ArrayList<>();
}
