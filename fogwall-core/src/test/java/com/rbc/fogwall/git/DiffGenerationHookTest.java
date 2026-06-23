package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
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

class DiffGenerationHookTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    ObjectId c1;
    ObjectId c2;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        c1 = commit("init.txt", "initial content").getId();
        c2 = commit("change.txt", "second content").getId();
    }

    private RevCommit commit(String filename, String content) throws Exception {
        File f = tempDir.resolve(filename).toFile();
        Files.writeString(f.toPath(), content + "\n");
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
    }

    @Test
    void branchPush_generatesDiffStep() {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1, c2, "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext ctx = new PushContext();

        new DiffGenerationHook(ctx).onPreReceive(rp, List.of(cmd));

        boolean hasDiff =
                ctx.getSteps().stream().anyMatch(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()));
        assertTrue(hasDiff, "branch push must generate a diff step");
    }

    @Test
    void tagPush_skipsDiffGeneration() {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), c2, "refs/tags/v1.0.0");
        PushContext ctx = new PushContext();

        new DiffGenerationHook(ctx).onPreReceive(rp, List.of(cmd));

        boolean hasDiff =
                ctx.getSteps().stream().anyMatch(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()));
        assertFalse(hasDiff, "tag push must not generate a diff step");
    }

    @Test
    void deleteCommand_skipped() {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1, ObjectId.zeroId(), "refs/heads/main", ReceiveCommand.Type.DELETE);
        PushContext ctx = new PushContext();

        new DiffGenerationHook(ctx).onPreReceive(rp, List.of(cmd));

        assertTrue(ctx.getSteps().isEmpty(), "delete command must not generate any steps");
    }

    // ---- effectiveFromId enrichment ----

    @Test
    void effectiveFromId_nonZero_usedAsDiffBase() throws Exception {
        // Simulate: c1 was forwarded upstream. c2 was locally approved but forwarding failed.
        // c3 is the new commit. effectiveFromId = c1 (last forwarded).
        // Diff should cover c1..c3 (includes c2 and c3 content), not c2..c3.
        ObjectId c3 = commit("extra.txt", "extra content").getId();

        ReceivePack rp = new ReceivePack(repo);
        // cmd represents the local-cache delta: c2 → c3
        ReceiveCommand cmd = new ReceiveCommand(c2, c3, "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext ctx = new PushContext();
        // PriorPushEnrichmentHook would have set effectiveFromId = c1 (last forwarded)
        ctx.setEffectiveFromId("refs/heads/main", c1.name());

        new DiffGenerationHook(ctx).onPreReceive(rp, List.of(cmd));

        var diffStep = ctx.getSteps().stream()
                .filter(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()))
                .findFirst()
                .orElseThrow();

        // The range log should record c1..c3, not c2..c3
        assertTrue(
                diffStep.getLogs().stream().anyMatch(l -> l.contains(c1.name()) && l.contains(c3.name())),
                "diff range should use effectiveFromId (c1) not cmd.getOldId() (c2)");
        // The diff content should include changes from c2 (change.txt) and c3 (extra.txt)
        assertNotNull(diffStep.getContent(), "diff content must not be null");
        assertTrue(diffStep.getContent().contains("extra.txt"), "diff must include c3 content");
    }

    @Test
    void effectiveFromId_zero_fallsBackToNormalNewBranchBehavior() throws Exception {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1, c2, "refs/heads/feature", ReceiveCommand.Type.UPDATE);
        PushContext ctx = new PushContext();
        // Enrichment hook sets zero base when nothing was ever forwarded for this branch
        ctx.setEffectiveFromId("refs/heads/feature", ObjectId.zeroId().name());

        new DiffGenerationHook(ctx).onPreReceive(rp, List.of(cmd));

        // A diff step should still be generated
        boolean hasDiff =
                ctx.getSteps().stream().anyMatch(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()));
        assertTrue(hasDiff, "diff step must be generated even with zero effectiveFromId");
    }
}
