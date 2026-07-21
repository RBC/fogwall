package com.rbc.fogwall.git;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.AuthorEmailCheck;
import com.rbc.fogwall.validation.Violation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * S&F-mode adapter for {@link AuthorEmailCheck}. Reads commits from the JGit repository, sends per-violation sideband
 * feedback, and records results in the shared {@link ValidationContext} and {@link PushContext}.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthorEmailValidationHook implements FogwallHook {

    private static final int ORDER = 250;

    private final CommitConfig commitConfig;
    private final ValidationContext validationContext;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        var check = new AuthorEmailCheck(commitConfig);
        Repository repo = rp.getRepository();
        List<Violation> allViolations = new ArrayList<>();
        boolean hadError = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                List<Violation> violations = check.check(getCommits(repo, cmd));
                for (Violation v : violations) {
                    validationContext.addIssue("checkAuthorEmails", v.reason(), v.formattedDetail());
                    allViolations.add(v);
                }
            } catch (Exception e) {
                // Fail closed: a validation control that cannot run must block the push, not silently pass.
                log.error("Failed to validate author emails for {}", cmd.getRefName(), e);
                validationContext.addError(
                        "checkAuthorEmails",
                        "author email validation could not complete for " + cmd.getRefName(),
                        "Validation error: " + e.getMessage());
                pushContext.addStep(PushStep.builder()
                        .stepName("checkAuthorEmails")
                        .stepOrder(ORDER)
                        .status(StepStatus.FAIL)
                        .errorMessage("Validation error: " + e.getMessage())
                        .build());
                hadError = true;
            }
        }

        if (allViolations.isEmpty() && !hadError) {
            pushContext.addStep(PushStep.builder()
                    .stepName("checkAuthorEmails")
                    .stepOrder(ORDER)
                    .status(StepStatus.PASS)
                    .build());
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "AuthorEmailValidationHook";
    }

    private List<Commit> getCommits(Repository repo, ReceiveCommand cmd) throws Exception {
        if (ObjectId.zeroId().equals(cmd.getOldId())) {
            return List.of(CommitInspectionService.getCommitDetails(
                    repo, cmd.getNewId().name()));
        }
        return CommitInspectionService.getCommitRange(
                repo, cmd.getOldId().name(), cmd.getNewId().name());
    }
}
