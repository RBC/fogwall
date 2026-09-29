package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.StepStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckEmptyBranchHookTest {

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

    private ObjectId createCommit(String message) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        f.createNewFile();
        Files.writeString(f.toPath(), message);
        git.add().addFilepattern(".").call();
        RevCommit c = git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage(message)
                .call();
        return c.getId();
    }

    // ---- tests ----

    @Test
    void normalUpdate_existingBranch_passes() throws Exception {
        // C1 → C2 on main: standard update, should find commits and pass
        ObjectId c1 = createCommit("First commit");
        ObjectId c2 = createCommit("Second commit");

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1, c2, "refs/heads/main", ReceiveCommand.Type.UPDATE);

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), new PushContext());
        hook.onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.NOT_ATTEMPTED, cmd.getResult(), "Update with new commits must not be rejected");
    }

    @Test
    void newBranch_atExistingCommit_passesAndNamesTheRef() throws Exception {
        // A branch created at a commit main already holds introduces nothing new, as when a developer branches off
        // work that is already upstream. There is no content to inspect, so the push continues.
        ObjectId existing = createCommit("on main");
        PushContext pushCtx = new PushContext();
        ValidationContext ctx = new ValidationContext();
        ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), existing, "refs/heads/feature");

        new CheckEmptyBranchHook(ctx, pushCtx).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertFalse(ctx.hasIssues(), "a branch at an existing commit is not an error");
        assertEquals(StepStatus.PASS, pushCtx.getSteps().get(0).getStatus());
        assertEquals(
                List.of("refs/heads/feature introduces no new commits"),
                pushCtx.getSteps().get(0).getLogs());
    }

    @Test
    void unreadableTip_blocks() throws Exception {
        // A tip that is not in the repository cannot be walked; the content checks would inspect nothing.
        createCommit("on main");
        ValidationContext ctx = new ValidationContext();
        ReceiveCommand cmd = new ReceiveCommand(
                ObjectId.zeroId(),
                ObjectId.fromString("1234567890123456789012345678901234567890"),
                "refs/heads/feature");

        new CheckEmptyBranchHook(ctx, new PushContext()).onPreReceive(new ReceivePack(repo), List.of(cmd));

        assertTrue(ctx.hasIssues(), "a push whose commits cannot be read must be blocked");
    }

    @Test
    void deleteCommand_skipped() throws Exception {
        // DELETE commands should not be checked for empty branch
        ObjectId c1 = createCommit("Commit");

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand deleteCmd =
                new ReceiveCommand(c1, ObjectId.zeroId(), "refs/heads/feature", ReceiveCommand.Type.DELETE);

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), new PushContext());
        hook.onPreReceive(rp, List.of(deleteCmd));

        assertEquals(
                ReceiveCommand.Result.NOT_ATTEMPTED,
                deleteCmd.getResult(),
                "Delete commands must be skipped by CheckEmptyBranchHook");
    }

    @Test
    void alreadyRejectedCommand_skipped() throws Exception {
        ObjectId c1 = createCommit("Commit");

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), c1, "refs/heads/x");
        cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "pre-rejected");

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), new PushContext());
        hook.onPreReceive(rp, List.of(cmd));

        // Result should remain the pre-set value - hook must not touch it
        assertEquals(ReceiveCommand.Result.REJECTED_OTHER_REASON, cmd.getResult());
        assertEquals("pre-rejected", cmd.getMessage());
    }

    @Test
    void lightweightTag_skipped() throws Exception {
        // Lightweight tags point directly to a commit — the empty-branch check must not fire
        ObjectId c1 = createCommit("Tagged commit");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), c1, "refs/tags/v1.0");

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), new PushContext());
        hook.onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.NOT_ATTEMPTED,
                cmd.getResult(),
                "Lightweight tag push must not be rejected by CheckEmptyBranchHook");
    }

    @Test
    void annotatedTag_skipped() throws Exception {
        // Annotated tags point to a tag object, not a commit directly
        ObjectId c1 = createCommit("Tagged commit");
        // Create an annotated tag via JGit
        ObjectInserter inserter = repo.newObjectInserter();
        TagBuilder tb = new TagBuilder();
        tb.setTag("v2.0");
        tb.setObjectId(repo.parseCommit(c1));
        tb.setTagger(new PersonIdent("Dev", "dev@example.com"));
        tb.setMessage("Release 2.0");
        ObjectId tagId = inserter.insert(tb);
        inserter.flush();

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), tagId, "refs/tags/v2.0");

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), new PushContext());
        hook.onPreReceive(rp, List.of(cmd));

        assertEquals(
                ReceiveCommand.Result.NOT_ATTEMPTED,
                cmd.getResult(),
                "Annotated tag push must not be rejected by CheckEmptyBranchHook");
    }

    @Test
    void step_recordedOnPass() throws Exception {
        ObjectId c1 = createCommit("First");
        ObjectId c2 = createCommit("Second");

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1, c2, "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pushCtx = new PushContext();

        CheckEmptyBranchHook hook = new CheckEmptyBranchHook(new ValidationContext(), pushCtx);
        hook.onPreReceive(rp, List.of(cmd));

        assertFalse(pushCtx.getSteps().isEmpty(), "A PASS step must be recorded");
    }
}
