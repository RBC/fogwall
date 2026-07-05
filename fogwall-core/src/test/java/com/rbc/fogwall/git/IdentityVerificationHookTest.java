package com.rbc.fogwall.git;

import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.user.UserEntry;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityVerificationHookTest {

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    PushIdentityResolver resolver;

    static final FogwallProvider GITHUB = new GitHubProvider("/proxy");

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();
        resolver = mock(PushIdentityResolver.class);
    }

    private RevCommit createCommit(String message, String authorEmail) throws Exception {
        File f = new File(tempDir.toFile(), UUID.randomUUID() + ".txt");
        writeString(f.toPath(), message);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", authorEmail))
                .setCommitter(new PersonIdent("Dev", authorEmail))
                .setMessage(message)
                .call();
    }

    private IdentityVerificationHook hook(
            CommitConfig.IdentityVerificationConfig config, ValidationContext vc, PushContext pc) {
        return new IdentityVerificationHook(resolver, config, vc, pc, GITHUB);
    }

    private static CommitConfig.IdentityVerificationConfig committerOnly(
            CommitConfig.IdentityVerificationMode committerMode) {
        return CommitConfig.IdentityVerificationConfig.builder()
                .committer(committerMode)
                .author(CommitConfig.IdentityVerificationMode.OFF)
                .build();
    }

    private static CommitConfig.IdentityVerificationConfig authorOnly(
            CommitConfig.IdentityVerificationMode authorMode) {
        return CommitConfig.IdentityVerificationConfig.builder()
                .committer(CommitConfig.IdentityVerificationMode.OFF)
                .author(authorMode)
                .build();
    }

    private static CommitConfig.IdentityVerificationConfig bothOff() {
        return CommitConfig.IdentityVerificationConfig.builder()
                .committer(CommitConfig.IdentityVerificationMode.OFF)
                .author(CommitConfig.IdentityVerificationMode.OFF)
                .build();
    }

    private static UserEntry alice() {
        return UserEntry.builder()
                .username("alice")
                .emails(List.of("alice@example.com"))
                .scmIdentities(List.of())
                .build();
    }

    // ---- mode=off → skips the check ----

    @Test
    void modeOff_skipsCheck_recordsPass() throws Exception {
        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        ValidationContext vc = new ValidationContext();

        hook(bothOff(), vc, pc).onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        assertFalse(pc.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pc.getSteps().get(0).getStatus());
        verifyNoInteractions(resolver);
    }

    // ---- null resolver (open mode) → always passes ----

    @Test
    void nullResolver_skipsCheck() throws Exception {
        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        ValidationContext vc = new ValidationContext();

        new IdentityVerificationHook(null, committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc, GITHUB)
                .onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        assertFalse(pc.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pc.getSteps().get(0).getStatus());
    }

    // ---- no pushUser in repo config → skip ----

    @Test
    void noPushUser_skipsCheck_recordsPass() throws Exception {
        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.WARN), vc, pc).onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        assertFalse(pc.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pc.getSteps().get(0).getStatus());
        verifyNoInteractions(resolver);
    }

    // ---- emails match → PASS ----

    @Test
    void emailsMatch_recordsPass() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        RevCommit c1 = createCommit("init", "alice@example.com");
        RevCommit c2 = createCommit("second", "alice@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc)
                .onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        assertFalse(pc.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pc.getSteps().get(0).getStatus());
    }

    // ---- committer strict + mismatch → blocked ----

    @Test
    void committerStrict_mismatch_addsIssue() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc)
                .onPreReceive(rp, List.of(cmd));

        assertTrue(vc.hasIssues());
        assertEquals(IdentityVerificationHook.STEP_NAME, vc.getIssues().get(0).hookName());
        assertTrue(vc.getIssues().get(0).summary().contains("alice"));
    }

    // ---- committer warn + mismatch → no issue, push proceeds ----

    @Test
    void committerWarn_mismatch_noIssue_recordsPass() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.WARN), vc, pc).onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues(), "WARN mode should not block the push");
        assertFalse(pc.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pc.getSteps().get(0).getStatus());
    }

    // ---- DELETE command → skipped entirely, no violation ----

    @Test
    void deleteCommand_skipped() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        RevCommit c1 = createCommit("init", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        // DELETE: newId is zero
        ReceiveCommand cmd = new ReceiveCommand(
                c1.getId(), org.eclipse.jgit.lib.ObjectId.zeroId(), "refs/heads/main", ReceiveCommand.Type.DELETE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc)
                .onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues(), "DELETE commands should not trigger identity violation");
    }

    // ---- rebase scenario: committer matches, author is external → passes when author=off ----

    @Test
    void rebaseScenario_committerMatches_authorExternal_authorOff_passes() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        // createCommit sets author=committer; we need a commit where committer=alice but author=external.
        // Use the lower-level JGit API to set them independently.
        File f = new File(tempDir.toFile(), java.util.UUID.randomUUID() + ".txt");
        writeString(f.toPath(), "content");
        git.add().addFilepattern(".").call();
        RevCommit base = git.commit()
                .setAuthor(new PersonIdent("Dev", "alice@example.com"))
                .setCommitter(new PersonIdent("Dev", "alice@example.com"))
                .setMessage("base")
                .call();
        git.add().addFilepattern(".").call();
        RevCommit rebased = git.commit()
                .setAuthor(new PersonIdent("External", "external@other.org")) // preserved author
                .setCommitter(new PersonIdent("Alice", "alice@example.com")) // rebaser is alice
                .setMessage("rebased external commit")
                .call();

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(base.getId(), rebased.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc)
                .onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues(), "Rebase should not be blocked when author check is off");
    }

    // ---- author strict + external author → blocked ----

    @Test
    void authorStrict_externalAuthor_addsIssue() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        File f = new File(tempDir.toFile(), java.util.UUID.randomUUID() + ".txt");
        writeString(f.toPath(), "content");
        git.add().addFilepattern(".").call();
        RevCommit base = git.commit()
                .setAuthor(new PersonIdent("Dev", "alice@example.com"))
                .setCommitter(new PersonIdent("Dev", "alice@example.com"))
                .setMessage("base")
                .call();
        git.add().addFilepattern(".").call();
        RevCommit rebased = git.commit()
                .setAuthor(new PersonIdent("External", "external@other.org"))
                .setCommitter(new PersonIdent("Alice", "alice@example.com"))
                .setMessage("rebased external commit")
                .call();

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(base.getId(), rebased.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(authorOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc).onPreReceive(rp, List.of(cmd));

        assertTrue(vc.hasIssues(), "Author strict should block external author email");
    }

    // ---- warn mode records step content with violation details ----

    @Test
    void committerWarn_mismatch_stepContentContainsViolations() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("alice-git"), isNull()))
                .thenReturn(Optional.of(alice()));

        RevCommit c1 = createCommit("init", "other@example.com");
        RevCommit c2 = createCommit("second", "other@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("alice-git");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.WARN), vc, pc).onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        var step = pc.getSteps().stream()
                .filter(s -> "identityVerification".equals(s.getStepName()))
                .findFirst();
        assertTrue(step.isPresent(), "WARN mode should record an identityVerification step");
        assertNotNull(step.get().getContent(), "Step content should contain violation details");
        assertTrue(step.get().getContent().contains("not in proxy user registry"));
    }

    // ---- resolver returns empty → skip ----

    @Test
    void resolverEmpty_skipsCheck_noStep() throws Exception {
        when(resolver.resolve(any(FogwallProvider.class), eq("unknown"), any())).thenReturn(Optional.empty());

        RevCommit c1 = createCommit("init", "someone@example.com");
        RevCommit c2 = createCommit("second", "someone@example.com");
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd = new ReceiveCommand(c1.getId(), c2.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);
        PushContext pc = new PushContext();
        pc.setPushUser("unknown");
        ValidationContext vc = new ValidationContext();

        hook(committerOnly(CommitConfig.IdentityVerificationMode.STRICT), vc, pc)
                .onPreReceive(rp, List.of(cmd));

        assertFalse(vc.hasIssues());
        assertTrue(pc.getSteps().isEmpty(), "No step should be recorded when user cannot be resolved");
    }
}
