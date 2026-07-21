package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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

class ContentPatternCommitMessageHookTest {

    @TempDir
    Path tempDir;

    Repository repo;
    ObjectId initialCommitId;

    @BeforeEach
    void setUp() throws Exception {
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        initialCommitId = createCommit(git, "Initial commit", "Dev User", "dev@example.com");
    }

    private ObjectId createCommit(Git git, String message, String name, String email) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        f.createNewFile();
        Files.writeString(f.toPath(), message);
        git.add().addFilepattern(".").call();
        RevCommit c = git.commit()
                .setAuthor(new PersonIdent(name, email))
                .setCommitter(new PersonIdent(name, email))
                .setMessage(message)
                .call();
        return c.getId();
    }

    private ContentPatternConfig enabledConfig() {
        return ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .build();
    }

    private ReceiveCommand newBranchCommand(ObjectId newCommit) {
        return new ReceiveCommand(ObjectId.zeroId(), newCommit, "refs/heads/test");
    }

    @Test
    void disabled_recordsSkippedStep() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId commit = createCommit(git, "ssn: 212-96-7431", "Dev", "dev@example.com");
        PushContext pushCtx = new PushContext();
        var hook = new ContentPatternCommitMessageHook(ContentPatternConfig.defaultConfig(), pushCtx);

        hook.onPreReceive(new ReceivePack(repo), List.of(newBranchCommand(commit)));

        assertEquals(1, pushCtx.getSteps().size());
        assertEquals(StepStatus.SKIPPED, pushCtx.getSteps().get(0).getStatus());
    }

    @Test
    void matchInMessage_recordsWarnStep_neverBlocks() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId commit = createCommit(git, "Fixed bug for customer, ssn: 212-96-7431", "Dev", "dev@example.com");
        PushContext pushCtx = new PushContext();
        var hook = new ContentPatternCommitMessageHook(enabledConfig(), pushCtx);

        hook.onPreReceive(new ReceivePack(repo), List.of(newBranchCommand(commit)));

        assertFalse(pushCtx.getSteps().isEmpty());
        var step = pushCtx.getSteps().get(0);
        assertEquals(StepStatus.WARN, step.getStatus());
        assertTrue(step.getContent().contains("Social Security Number"));
        assertFalse(step.getContent().contains("212-96-7431"), "raw matched value must never appear in step content");
    }

    @Test
    void scanCommitMessagesDisabled_recordsSkippedStep_evenWhenEnabled() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId commit = createCommit(git, "ssn: 212-96-7431", "Dev", "dev@example.com");
        PushContext pushCtx = new PushContext();
        ContentPatternConfig config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanCommitMessages(false)
                .build();
        var hook = new ContentPatternCommitMessageHook(config, pushCtx);

        hook.onPreReceive(new ReceivePack(repo), List.of(newBranchCommand(commit)));

        assertEquals(1, pushCtx.getSteps().size());
        assertEquals(StepStatus.SKIPPED, pushCtx.getSteps().get(0).getStatus());
    }

    @Test
    void cleanMessage_recordsPassStep() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId commit = createCommit(git, "Fix login bug", "Dev", "dev@example.com");
        PushContext pushCtx = new PushContext();
        var hook = new ContentPatternCommitMessageHook(enabledConfig(), pushCtx);

        hook.onPreReceive(new ReceivePack(repo), List.of(newBranchCommand(commit)));

        assertFalse(pushCtx.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pushCtx.getSteps().get(0).getStatus());
    }

    @Test
    void deleteCommand_skipped() throws Exception {
        PushContext pushCtx = new PushContext();
        var hook = new ContentPatternCommitMessageHook(enabledConfig(), pushCtx);
        ReceiveCommand deleteCmd =
                new ReceiveCommand(initialCommitId, ObjectId.zeroId(), "refs/heads/test", ReceiveCommand.Type.DELETE);

        hook.onPreReceive(new ReceivePack(repo), List.of(deleteCmd));

        assertFalse(pushCtx.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pushCtx.getSteps().get(0).getStatus());
    }
}
