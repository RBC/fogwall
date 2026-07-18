package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.BinaryBlobConfig;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryBlobDetectionHookTest {

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

    private void runHook(
            BinaryBlobConfig config,
            ValidationContext ctx,
            PushContext pushCtx,
            ObjectId oldId,
            ObjectId newId,
            String ref) {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(oldId, newId, ref, ReceiveCommand.Type.UPDATE);
        new BinaryBlobDetectionHook(config, ctx, pushCtx).onPreReceive(rp, List.of(cmd));
    }

    @Test
    void disabled_skipsEntirely() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit tip = commitBytes("big.bin", new byte[2048]);

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(false).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/heads/main");

        assertFalse(ctx.hasIssues());
    }

    @Test
    void enabled_oversizedBlob_fails() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit tip = commitBytes("big.bin", new byte[2048]);

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/heads/main");

        assertTrue(ctx.hasIssues());
    }

    @Test
    void enabled_cleanPush_passes() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit tip = commitText("readme.txt", "clean text content");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/heads/main");

        assertFalse(ctx.hasIssues());
    }

    @Test
    void deleteCommand_skipped() throws Exception {
        RevCommit c1 = commitBytes("big.bin", new byte[2048]);

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(c1.getId(), ObjectId.zeroId(), "refs/heads/main", ReceiveCommand.Type.DELETE);
        new BinaryBlobDetectionHook(config, ctx, pushCtx).onPreReceive(rp, List.of(cmd));

        assertFalse(ctx.hasIssues());
    }

    @Test
    void tagPush_skipped() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit tip = commitBytes("big.bin", new byte[2048]);

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/tags/v1.0.0");

        assertFalse(ctx.hasIssues());
    }

    @Test
    void deniedMimeType_fails() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit tip = commitBytes("doc.pdf", "%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII));

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config = BinaryBlobConfig.builder()
                .enabled(true)
                .denyMimeTypes(List.of("application/pdf"))
                .build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/heads/main");

        assertTrue(ctx.hasIssues());
    }

    // ---- per-commit scan (intermediate-commit smuggling) ----

    /**
     * A blob added in commit B and removed by commit C produces a clean aggregate diff A→C, but must still be caught by
     * the per-commit pass scanning A→B individually. Same bypass DiffScanningHook's per-commit pass closes for text
     * content (RBC/fogwall#339).
     */
    @Test
    void perCommitScan_oversizedBlobAddedInIntermediateCommitThenRemoved_stillCaught() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        // Commit B: adds an oversized blob
        RevCommit intermediate = commitBytes("big.bin", new byte[2048]);
        // Commit C: removes it — aggregate diff base..C is clean
        Files.delete(tempDir.resolve("big.bin"));
        git.rm().addFilepattern("big.bin").call();
        RevCommit tip = git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("remove big.bin")
                .call();

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), tip.getId(), "refs/heads/main");

        assertTrue(
                ctx.hasIssues(),
                "oversized blob smuggled in intermediate commit " + intermediate.getName()
                        + " must still be flagged even though the aggregate diff is clean");
    }

    @Test
    void perCommitScan_multipleCleanCommits_passes() throws Exception {
        RevCommit base = commitText(".gitkeep", "");
        RevCommit c1 = commitText("a.txt", "clean");
        RevCommit c2 = commitText("b.txt", "still clean");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        BinaryBlobConfig config =
                BinaryBlobConfig.builder().enabled(true).maxSizeBytes(1024).build();
        runHook(config, ctx, pushCtx, base.getId(), c2.getId(), "refs/heads/main");

        assertFalse(ctx.hasIssues());
    }
}
