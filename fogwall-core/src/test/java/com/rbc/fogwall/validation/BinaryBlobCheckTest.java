package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.git.CommitInspectionService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryBlobCheckTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
    }

    private RevCommit commitBytes(String filename, byte[] content) throws Exception {
        File f = tempDir.resolve(filename).toFile();
        Files.write(f.toPath(), content);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
    }

    private RevCommit commitText(String filename, String content) throws Exception {
        return commitBytes(filename, (content + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private RevCommit emptyBase() throws Exception {
        return commitText(".gitkeep", "");
    }

    private List<DiffEntry> diffFromBase(RevCommit base, RevCommit tip) throws Exception {
        return CommitInspectionService.getDiff(repo, base.getName(), tip.getName());
    }

    // ---- defaultConfig ----

    @Test
    void defaultConfig_isDisabledWithNoLimitsOrDenials() {
        BinaryBlobConfig config = BinaryBlobConfig.defaultConfig();

        assertFalse(config.isEnabled());
        assertEquals(0, config.getMaxSizeBytes());
        assertTrue(config.getDenyMimeTypes().isEmpty());
    }

    // ---- enabled/disabled ----

    @Test
    void disabledConfig_neverFlags() throws Exception {
        RevCommit base = emptyBase();
        RevCommit tip = commitBytes("big.bin", new byte[1000]);

        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(false).maxSizeBytes(10).build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    @Test
    void emptyDiff_returnsNoViolations() throws Exception {
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1).build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, List.of());

        assertTrue(violations.isEmpty());
    }

    // ---- size threshold ----

    @Test
    void blobExceedsMaxSize_flagged() throws Exception {
        RevCommit base = emptyBase();
        RevCommit tip = commitBytes("big.bin", new byte[2048]);

        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).reason().contains("exceeds max blob size"));
    }

    @Test
    void blobUnderMaxSize_notFlagged() throws Exception {
        RevCommit base = emptyBase();
        RevCommit tip = commitBytes("small.bin", new byte[10]);

        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    @Test
    void zeroMaxSize_meansNoSizeLimit() throws Exception {
        RevCommit base = emptyBase();
        RevCommit tip = commitBytes("big.bin", new byte[1_000_000]);

        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(0).build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    // ---- MIME sniffing ----

    @Test
    void pdfMagicBytes_detectedAndFlaggedWhenDenied() throws Exception {
        RevCommit base = emptyBase();
        byte[] pdfHeader = "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII);
        RevCommit tip = commitBytes("doc.pdf", pdfHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/pdf"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).reason().contains("application/pdf"));
    }

    @Test
    void zipMagicBytes_detectedAndFlaggedWhenDenied() throws Exception {
        RevCommit base = emptyBase();
        byte[] zipHeader = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        RevCommit tip = commitBytes("archive.zip", zipHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/zip"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertEquals(1, violations.size());
    }

    @Test
    void gzipMagicBytes_detectedAndFlaggedWhenDenied() throws Exception {
        RevCommit base = emptyBase();
        byte[] gzipHeader = new byte[] {0x1F, (byte) 0x8B, 0x08, 0x00};
        RevCommit tip = commitBytes("archive.tar.gz", gzipHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/gzip"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertEquals(1, violations.size());
    }

    @Test
    void javaClassMagicBytes_detectedAndFlaggedWhenDenied() throws Exception {
        RevCommit base = emptyBase();
        byte[] classHeader = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00};
        RevCommit tip = commitBytes("Main.class", classHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/java-vm"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertEquals(1, violations.size());
    }

    @Test
    void pdfBlob_notFlaggedWhenMimeTypeNotDenied() throws Exception {
        RevCommit base = emptyBase();
        byte[] pdfHeader = "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII);
        RevCommit tip = commitBytes("doc.pdf", pdfHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/zip"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    @Test
    void textFile_neverMatchesAnyMimeSignature() throws Exception {
        RevCommit base = emptyBase();
        RevCommit tip = commitText("readme.txt", "just plain text, nothing binary here");

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(
                        List.of("application/pdf", "application/zip", "application/gzip", "application/x-executable"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    @Test
    void emptyDenyMimeTypes_skipsSniffingEntirely() throws Exception {
        RevCommit base = emptyBase();
        byte[] pdfHeader = "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII);
        RevCommit tip = commitBytes("doc.pdf", pdfHeader);

        BinaryBlobConfig config = BinaryBlobConfig.builder().enabled(true).build(); // denyMimeTypes defaults empty
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }

    // ---- deletions ----

    @Test
    void deletedFile_notFlagged() throws Exception {
        RevCommit base = commitBytes("doc.pdf", "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII));
        Files.delete(tempDir.resolve("doc.pdf"));
        git.rm().addFilepattern("doc.pdf").call();
        RevCommit tip = git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("remove doc.pdf")
                .call();

        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .maxSizeBytes(1)
                .denyMimeTypes(List.of("application/pdf"))
                .build();
        List<Violation> violations = new BinaryBlobCheck(config).check(repo, diffFromBase(base, tip));

        assertTrue(violations.isEmpty());
    }
}
