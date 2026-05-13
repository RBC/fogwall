package org.finos.gitproxy.git;

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
}
