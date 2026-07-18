package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Runtime configuration for push-level binary blob detection. Flags added/modified blobs in a push that exceed a size
 * threshold or match a denied MIME type.
 *
 * <p>MIME type classification is content-based (magic-byte signature sniffing on the first few bytes of the blob), not
 * filename-extension-based — extensions are trivially renamed and are a weaker signal. No dependency like Apache Tika
 * is used; JGit exposes object size from pack data and streams blob content without loading it fully, so detection
 * never reads more than a small header from each blob.
 *
 * <p>ENFORCE-only: a match always blocks the push. There is no advisory/WARN mode (see RBC/fogwall#141).
 *
 * <p>Hot-reloadable via {@code POST /api/config/reload?section=binary-blob}.
 */
@Data
@Builder
public class BinaryBlobConfig {

    @Builder.Default
    private boolean enabled = false;

    /** Maximum allowed blob size in bytes. {@code 0} means no size limit. */
    @Builder.Default
    private long maxSizeBytes = 0;

    /** Denied MIME types, matched via magic-byte signature sniffing (e.g. {@code "application/pdf"}). */
    @Builder.Default
    private List<String> denyMimeTypes = new ArrayList<>();

    public static BinaryBlobConfig defaultConfig() {
        return BinaryBlobConfig.builder().build();
    }
}
