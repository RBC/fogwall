package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.StepStatus;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the fail-closed behavior of store-and-forward validation hooks. When a check cannot run — e.g.
 * the pushed commit can't be read from the repository — the hook must record a blocking error, not a silent PASS, so a
 * malformed push cannot slip an unvalidated change past a control. See {@code ValidationContext#addError}.
 */
class ValidationHookFailClosedTest {

    @TempDir
    Path tempDir;

    @Test
    void authorEmailHook_whenCommitUnreadable_failsClosed() throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Repository repo = git.getRepository();
            ReceivePack rp = new ReceivePack(repo);

            // New-branch push whose tip is an object that does not exist in the repo — reading it throws.
            ObjectId missing = ObjectId.fromString("0123456789012345678901234567890123456789");
            ReceiveCommand cmd = new ReceiveCommand(ObjectId.zeroId(), missing, "refs/heads/main");

            var validationContext = new ValidationContext();
            var pushContext = new PushContext();

            new AuthorEmailValidationHook(CommitConfig.defaultConfig(), validationContext, pushContext)
                    .onPreReceive(rp, List.of(cmd));

            assertTrue(validationContext.hasIssues(), "an unreadable commit must block the push (fail closed)");
            assertTrue(
                    validationContext.getIssues().get(0).error(),
                    "the blocking issue must be flagged as an error, not a policy violation");
            assertTrue(
                    pushContext.getSteps().stream().anyMatch(s -> s.getStatus() == StepStatus.FAIL),
                    "a FAIL step must be recorded for operator visibility");
            assertFalse(
                    pushContext.getSteps().stream().anyMatch(s -> s.getStatus() == StepStatus.PASS),
                    "must not record PASS when the check could not run");
        }
    }

    @Test
    void addError_blocksAndIsFlaggedAsError() {
        var ctx = new ValidationContext();
        ctx.addError("someHook", "could not complete", "boom");

        assertTrue(ctx.hasIssues(), "an error must block the push");
        assertTrue(ctx.getIssues().get(0).error(), "the issue must be flagged as an error");
    }
}
