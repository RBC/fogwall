package com.rbc.fogwall.validation;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.CROSS_MARK;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.BinaryBlobConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;

/**
 * Flags added/modified blobs in a diff that exceed a configured size threshold or match a denied MIME type.
 *
 * <p>Size is read directly from the JGit {@link ObjectLoader} (available from pack data without loading blob content).
 * MIME type classification sniffs the first few bytes of blob content against a built-in table of magic byte signatures
 * — a file's extension is not consulted, since renaming a file to defeat an extension check is trivial and extensions
 * are not a reliable content signal across operating systems. Only a small, bounded header is read per blob; the rest
 * of the object is never loaded.
 */
public final class BinaryBlobCheck {

    /**
     * Magic-byte signature → MIME type, checked in order against the start of each blob's content. ZIP-based Office
     * formats (docx/xlsx/pptx) and plain zip/jar archives share the same {@code PK\x03\x04} container signature and
     * cannot be distinguished without deeper OOXML-aware parsing, so they are all classified as
     * {@code application/zip}.
     *
     * <p>Deliberately excludes signatures that are either too generic to trust (e.g. PKCS#12/PFX's {@code 30 82} is a
     * bare ASN.1 DER SEQUENCE header shared by many unrelated formats) or require reading past a small fixed header
     * (e.g. ISO9660's magic sits at a 32KB offset; DuckDB's header is not stable across versions).
     */
    private static final List<Signature> SIGNATURES = List.of(
            new Signature(new byte[] {'%', 'P', 'D', 'F'}, "application/pdf"),
            new Signature(new byte[] {0x50, 0x4B, 0x03, 0x04}, "application/zip"),
            new Signature(new byte[] {0x50, 0x4B, 0x05, 0x06}, "application/zip"),
            new Signature(new byte[] {0x50, 0x4B, 0x07, 0x08}, "application/zip"),
            new Signature(new byte[] {0x1F, (byte) 0x8B}, "application/gzip"),
            new Signature(new byte[] {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}, "application/x-7z-compressed"),
            new Signature(new byte[] {'R', 'a', 'r', '!', 0x1A, 0x07}, "application/vnd.rar"),
            new Signature(new byte[] {0x7F, 'E', 'L', 'F'}, "application/x-executable"),
            new Signature(new byte[] {'M', 'Z'}, "application/x-msdownload"),
            new Signature("SQLite format 3\0".getBytes(StandardCharsets.US_ASCII), "application/vnd.sqlite3"),
            new Signature(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, "application/java-vm"),
            new Signature(new byte[] {0x00, 'a', 's', 'm'}, "application/wasm"),
            new Signature(new byte[] {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD}, "application/zstd"),
            new Signature(new byte[] {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00}, "application/x-xz"),
            new Signature(new byte[] {'B', 'Z', 'h'}, "application/x-bzip2"),
            new Signature(new byte[] {'P', 'A', 'R', '1'}, "application/vnd.apache.parquet"),
            new Signature(
                    new byte[] {(byte) 0xFE, (byte) 0xED, (byte) 0xFE, (byte) 0xED}, "application/x-java-keystore"),
            new Signature(
                    new byte[] {(byte) 0xCE, (byte) 0xCE, (byte) 0xCE, (byte) 0xCE}, "application/x-java-jce-keystore"),
            new Signature(new byte[] {'Q', 'F', 'I', (byte) 0xFB}, "application/x-qemu-disk"),
            new Signature(new byte[] {(byte) 0x89, 'H', 'D', 'F', '\r', '\n', 0x1A, '\n'}, "application/x-hdf5"));

    private static final int SNIFF_BYTES = 16;

    private final BinaryBlobConfig config;

    public BinaryBlobCheck(BinaryBlobConfig config) {
        this.config = config;
    }

    /**
     * Checks every added/modified blob in {@code diffs} against the configured size threshold and MIME denylist.
     * Deleted files are skipped — this check only cares about content entering the repository.
     */
    public List<Violation> check(Repository repository, List<DiffEntry> diffs) throws IOException {
        List<Violation> violations = new ArrayList<>();
        if (!config.isEnabled() || diffs.isEmpty()) {
            return violations;
        }

        try (ObjectReader reader = repository.newObjectReader()) {
            for (DiffEntry entry : diffs) {
                if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue;
                }

                if (entry.getNewMode() != null && entry.getNewMode().getObjectType() != Constants.OBJ_BLOB) {
                    // Submodule pointers (GITLINK) and other non-blob modes have no local blob content to inspect.
                    continue;
                }

                ObjectId blobId = entry.getNewId().toObjectId();
                if (blobId == null || ObjectId.zeroId().equals(blobId)) {
                    continue;
                }

                String path = entry.getNewPath();
                ObjectLoader loader = reader.open(blobId);

                long size = loader.getSize();

                if (config.getMaxSizeBytes() > 0 && size > config.getMaxSizeBytes()) {
                    violations.add(violation(
                            path,
                            String.format(
                                    Locale.ROOT,
                                    "%s (%,d bytes) exceeds max blob size of %,d bytes",
                                    path,
                                    size,
                                    config.getMaxSizeBytes())));
                }

                if (!config.getDenyMimeTypes().isEmpty()) {
                    String mimeType = sniff(loader);
                    if (mimeType != null && config.getDenyMimeTypes().contains(mimeType)) {
                        violations.add(violation(path, path + " has a denied content type: " + mimeType));
                    }
                }
            }
        }

        return violations;
    }

    /** Reads a small header from the blob and matches it against {@link #SIGNATURES}. Never loads the full blob. */
    private static String sniff(ObjectLoader loader) throws IOException {
        byte[] header;
        try (ObjectStream stream = loader.openStream()) {
            header = stream.readNBytes(SNIFF_BYTES);
        }
        for (Signature signature : SIGNATURES) {
            if (matches(header, signature.magic())) {
                return signature.mimeType();
            }
        }
        return null;
    }

    private static boolean matches(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static Violation violation(String subject, String reason) {
        String detail = sym(CROSS_MARK) + "  " + reason + "\n"
                + "  → Remove the file from the push, or configure validation.binary-blob to allow it.";
        return new Violation(subject, reason, detail);
    }

    private record Signature(byte[] magic, String mimeType) {}
}
