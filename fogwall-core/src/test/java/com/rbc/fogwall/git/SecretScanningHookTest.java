package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.SecretScanConfig;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

class SecretScanningHookTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    GitleaksRunner runner;
    ValidationContext validationContext;
    PushContext pushContext;
    SecretScanConfig config;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        runner = mock(GitleaksRunner.class);
        validationContext = new ValidationContext();
        pushContext = new PushContext();
        config = SecretScanConfig.builder().build();
        when(runner.scanGit(any(), any(), any(), any())).thenReturn(Optional.of(List.of()));
    }

    private RevCommit createCommit(String msg) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        Files.writeString(f.toPath(), msg);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage(msg)
                .call();
    }

    private SecretScanningHook hook() {
        return new SecretScanningHook(config, validationContext, pushContext, runner);
    }

    // ---- normal push: uses cmd.getOldId() ----

    @Test
    void normalPush_usesCommandOldIdAsCommitFrom() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        hook().onPreReceive(rp, List.of(cmd));

        verify(runner).scanGit(any(), eq(c1.getId().name()), eq(c2.getId().name()), any());
    }

    // ---- re-push with non-zero effectiveFromId: uses effectiveFrom, not cmd.getOldId() ----

    @Test
    void repush_nonZeroEffectiveFromId_usedAsCommitFrom() throws Exception {
        RevCommit c1 = createCommit("A"); // last forwarded
        RevCommit c2 = createCommit("B"); // cached locally, not forwarded (local ref tip)
        RevCommit c3 = createCommit("C"); // new commit
        ReceivePack rp = new ReceivePack(repo);
        // cmd represents local delta: c2 → c3
        ReceiveCommand cmd = new ReceiveCommand(c2.getId(), c3.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        // Enrichment hook detected c2 was not forwarded; effective base is c1
        pushContext.setEffectiveFromId("refs/heads/main", c1.getId().name());

        hook().onPreReceive(rp, List.of(cmd));

        // Must scan from c1 (last forwarded) to c3 — covers c2 and c3, not just c3
        verify(runner).scanGit(any(), eq(c1.getId().name()), eq(c3.getId().name()), any());
        verify(runner, never()).scanGit(any(), eq(c2.getId().name()), any(), any());
    }

    // ---- re-push with zero effectiveFromId: falls back to cmd.getOldId() (zero case) ----

    @Test
    void repush_zeroEffectiveFromId_fallsBackToCommandOldId() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        // Enrichment hook set zero (nothing was ever forwarded for this branch)
        pushContext.setEffectiveFromId("refs/heads/main", ObjectId.zeroId().name());

        hook().onPreReceive(rp, List.of(cmd));

        // Falls back to cmd.getOldId() since zero effective base is not actionable
        verify(runner).scanGit(any(), eq(c1.getId().name()), eq(c2.getId().name()), any());
    }

    // ---- findings → adds validation issue ----

    @Test
    void findings_addsValidationIssue() throws Exception {
        RevCommit c1 = createCommit("A");
        RevCommit c2 = createCommit("B");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        GitleaksRunner.Finding finding = mock(GitleaksRunner.Finding.class);
        when(finding.toMessage()).thenReturn("SECRET: AWS key found in config.yml");
        when(runner.scanGit(any(), any(), any(), any())).thenReturn(Optional.of(List.of(finding)));

        hook().onPreReceive(rp, List.of(cmd));

        assertTrue(validationContext.hasIssues(), "secret finding must become a validation issue");
    }

    // ---- delete command skipped ----

    @Test
    void deleteCommand_skipped() throws Exception {
        RevCommit c1 = createCommit("A");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(c1.getId(), ObjectId.zeroId(), "refs/heads/main", ReceiveCommand.Type.DELETE);

        hook().onPreReceive(rp, List.of(cmd));

        verifyNoInteractions(runner);
    }
}
