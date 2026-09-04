package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.EmailRule;
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

class AuthorEmailValidationHookTest {

    @TempDir
    Path tempDir;

    Repository repo;
    ObjectId commitId1;
    ObjectId commitId2;

    @BeforeEach
    void setUp() throws Exception {
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        // Disable GPG signing in the test repo to avoid UnsupportedSigningFormatException
        // when the user's global git config has commit.gpgsign=true
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();

        commitId1 = createCommit(git, "First commit", "Dev User", "dev@example.com");
        commitId2 = createCommit(git, "Second commit", "Dev User", "dev@example.com");
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

    private CommitConfig allowExampleCom() {
        return CommitConfig.builder()
                .committer(CommitConfig.CommitterConfig.builder()
                        .email(CommitConfig.EmailConfig.builder()
                                .rules(List.of(
                                        EmailRule.allow(
                                                EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, "example\\.com$"),
                                        EmailRule.block(
                                                EmailRule.Field.LOCAL, EmailRule.Match.REGEX, "^(noreply|bot)$")))
                                .build())
                        .build())
                .build();
    }

    private ReceivePack makeReceivePack() {
        return new ReceivePack(repo);
    }

    private ReceiveCommand newBranchCommand(ObjectId newCommit) {
        return new ReceiveCommand(ObjectId.zeroId(), newCommit, "refs/heads/test");
    }

    private ReceiveCommand updateCommand(ObjectId oldCommit, ObjectId newCommit) {
        return new ReceiveCommand(oldCommit, newCommit, "refs/heads/main");
    }

    // ---- tests ----

    @Test
    void validEmail_noIssues() throws Exception {
        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(commitId1);

        hook.onPreReceive(rp, List.of(cmd));

        assertFalse(ctx.hasIssues(), "Valid email must not produce issues");
    }

    @Test
    void invalidDomain_addsIssue() throws Exception {
        // Create a commit with a bad email domain
        Git git = Git.open(tempDir.toFile());
        ObjectId badCommit = createCommit(git, "Bad domain commit", "Outsider", "dev@gmail.com");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(badCommit);

        hook.onPreReceive(rp, List.of(cmd));

        assertTrue(ctx.hasIssues(), "Invalid domain must produce an issue");
    }

    @Test
    void blockedLocalPart_addsIssue() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId noReplyCommit = createCommit(git, "noreply commit", "Bot", "noreply@example.com");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();
        ReceiveCommand cmd = newBranchCommand(noReplyCommit);

        hook.onPreReceive(rp, List.of(cmd));

        assertTrue(ctx.hasIssues(), "Blocked local part must produce an issue");
    }

    @Test
    void validEmail_recordsPassStep() throws Exception {
        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();

        hook.onPreReceive(rp, List.of(newBranchCommand(commitId1)));

        assertFalse(pushCtx.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, pushCtx.getSteps().get(0).getStatus(), "Valid email must record PASS step");
    }

    @Test
    void invalidEmail_recordsFailStep() throws Exception {
        Git git = Git.open(tempDir.toFile());
        ObjectId badCommit = createCommit(git, "Bad commit", "Outsider", "dev@badomain.io");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();

        hook.onPreReceive(rp, List.of(newBranchCommand(badCommit)));

        assertFalse(ctx.getIssues().isEmpty());
        assertEquals("not in allowlist", ctx.getIssues().get(0).summary(), "Invalid email must record FAIL step");
    }

    @Test
    void deleteCommand_skipped() throws Exception {
        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();

        // DELETE type: oldId is a real commit, newId is zero
        ReceiveCommand deleteCmd =
                new ReceiveCommand(commitId1, ObjectId.zeroId(), "refs/heads/test", ReceiveCommand.Type.DELETE);

        hook.onPreReceive(rp, List.of(deleteCmd));

        // Hook should skip DELETE commands - no issues from a deletion
        assertFalse(ctx.hasIssues(), "DELETE command must not trigger email validation");
    }

    @Test
    void updateCommand_validEmailRange_noIssues() throws Exception {
        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();
        // Both commits are in the repo with "dev@example.com"
        ReceiveCommand cmd = updateCommand(commitId1, commitId2);

        hook.onPreReceive(rp, List.of(cmd));

        assertFalse(ctx.hasIssues());
    }

    @Test
    void noConfig_anyEmailAllowed() throws Exception {
        Git git = Git.open(tempDir.toFile());
        // Create commit with an unusual email - should pass with default config
        ObjectId anyCommit = createCommit(git, "Free email commit", "User", "dev@unknown.io");

        ValidationContext ctx = new ValidationContext();
        PushContext pushCtx = new PushContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(CommitConfig.defaultConfig(), ctx, pushCtx);
        ReceivePack rp = makeReceivePack();

        hook.onPreReceive(rp, List.of(newBranchCommand(anyCommit)));

        assertFalse(ctx.hasIssues(), "Default config must not reject any valid email");
    }

    // ---- annotated tag tagger identity ----

    /** Creates an annotated tag on HEAD with the given tagger and returns a push command for its ref. */
    private ReceiveCommand annotatedTagCommand(String taggerName, String taggerEmail) throws Exception {
        Git git = Git.open(tempDir.toFile());
        var ref = git.tag()
                .setName("v1.0.0")
                .setMessage("Release 1.0.0")
                .setTagger(new PersonIdent(taggerName, taggerEmail))
                .setAnnotated(true)
                .call();
        return new ReceiveCommand(ObjectId.zeroId(), ref.getObjectId(), "refs/tags/v1.0.0");
    }

    @Test
    void annotatedTag_allowedTaggerDomain_noIssues() throws Exception {
        ValidationContext ctx = new ValidationContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, new PushContext());

        hook.onPreReceive(makeReceivePack(), List.of(annotatedTagCommand("Releaser", "releaser@example.com")));

        assertFalse(ctx.hasIssues(), "Tagger inside the allowed domain must not produce issues");
    }

    @Test
    void annotatedTag_disallowedTaggerDomain_addsIssue() throws Exception {
        ValidationContext ctx = new ValidationContext();
        AuthorEmailValidationHook hook = new AuthorEmailValidationHook(allowExampleCom(), ctx, new PushContext());

        hook.onPreReceive(makeReceivePack(), List.of(annotatedTagCommand("Outsider", "releaser@gmail.com")));

        assertTrue(ctx.hasIssues(), "Tagger outside the allowed domain must be held to the committer policy");
    }

    @Test
    void annotatedTag_noConfig_anyTaggerAllowed() throws Exception {
        ValidationContext ctx = new ValidationContext();
        AuthorEmailValidationHook hook =
                new AuthorEmailValidationHook(CommitConfig.defaultConfig(), ctx, new PushContext());

        hook.onPreReceive(makeReceivePack(), List.of(annotatedTagCommand("Anyone", "anyone@anywhere.io")));

        assertFalse(ctx.hasIssues(), "Default config must not reject any tagger");
    }
}
