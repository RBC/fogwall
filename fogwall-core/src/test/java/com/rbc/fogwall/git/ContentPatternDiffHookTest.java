package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
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

class ContentPatternDiffHookTest {

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

    private ContentPatternConfig enabledConfig() {
        return ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .build();
    }

    /** Runs diff generation then the content-pattern diff hook against a single UPDATE command. */
    private void runHooks(ContentPatternConfig config, PushContext pushCtx, ObjectId oldId, ObjectId newId) {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(oldId, newId, "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new DiffGenerationHook(new ValidationContext(), pushCtx).onPreReceive(rp, List.of(cmd));
        new ContentPatternDiffHook(config, pushCtx).onPreReceive(rp, List.of(cmd));
    }

    @Test
    void disabled_recordsSkippedStep() throws Exception {
        RevCommit c1 = commit("a.txt", "clean");
        RevCommit c2 = commit("customer.txt", "ssn on file: 212-96-7431");

        PushContext pushCtx = new PushContext();
        runHooks(ContentPatternConfig.defaultConfig(), pushCtx, c1.getId(), c2.getId());

        var step = pushCtx.getSteps().stream()
                .filter(s -> s.getStepName().equals("scanContentPatternsDiff"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.SKIPPED, step.getStatus());
    }

    @Test
    void matchInDiff_recordsWarnStep_neverBlocks_andQueuesRedaction() throws Exception {
        RevCommit c1 = commit("a.txt", "clean");
        RevCommit c2 = commit("customer.txt", "ssn on file: 212-96-7431");

        PushContext pushCtx = new PushContext();
        runHooks(enabledConfig(), pushCtx, c1.getId(), c2.getId());

        var step = pushCtx.getSteps().stream()
                .filter(s -> s.getStepName().equals("scanContentPatternsDiff"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.WARN, step.getStatus());
        assertTrue(step.getContent().contains("Social Security Number"));
        assertFalse(step.getContent().contains("212-96-7431"), "raw matched value must never appear in step content");
        assertTrue(pushCtx.getSecretsToRedact().contains("212-96-7431"));
    }

    @Test
    void scanDiffDisabled_recordsSkippedStep_evenWhenEnabled() throws Exception {
        RevCommit c1 = commit("a.txt", "clean");
        RevCommit c2 = commit("customer.txt", "ssn on file: 212-96-7431");

        ContentPatternConfig config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanDiff(false)
                .build();

        PushContext pushCtx = new PushContext();
        runHooks(config, pushCtx, c1.getId(), c2.getId());

        var step = pushCtx.getSteps().stream()
                .filter(s -> s.getStepName().equals("scanContentPatternsDiff"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.SKIPPED, step.getStatus());
    }

    @Test
    void cleanDiff_recordsPassStep() throws Exception {
        RevCommit c1 = commit("a.txt", "clean");
        RevCommit c2 = commit("b.txt", "also clean content");

        PushContext pushCtx = new PushContext();
        runHooks(enabledConfig(), pushCtx, c1.getId(), c2.getId());

        var step = pushCtx.getSteps().stream()
                .filter(s -> s.getStepName().equals("scanContentPatternsDiff"))
                .findFirst()
                .orElseThrow();
        assertEquals(StepStatus.PASS, step.getStatus());
    }
}
