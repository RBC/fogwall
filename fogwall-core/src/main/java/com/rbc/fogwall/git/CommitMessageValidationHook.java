package com.rbc.fogwall.git;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.CommitMessageCheck;
import com.rbc.fogwall.validation.Violation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * server-mode adapter for {@link CommitMessageCheck}. Reads commits from the JGit repository, sends per-violation
 * sideband feedback, and records results in the shared {@link ValidationContext} and {@link PushContext}.
 */
@Slf4j
@RequiredArgsConstructor
public final class CommitMessageValidationHook implements MandatoryFogwallHook {

    private final CommitConfig commitConfig;
    private final ValidationContext validationContext;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        var check = new CommitMessageCheck(commitConfig);
        Repository repo = rp.getRepository();
        List<Violation> allViolations = new ArrayList<>();
        boolean hadError = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                // getCommits peels annotated tags to their target commit, so the tag's own annotation message is never
                // seen. It is authored text like a commit message, so validate it through the same check (#474).
                List<Commit> toCheck = new ArrayList<>(getCommits(repo, cmd));
                CommitInspectionService.getAnnotatedTagMessage(repo, cmd.getNewId())
                        .ifPresent(
                                msg -> toCheck.add(Commit.builder().message(msg).build()));
                List<Violation> violations = check.check(toCheck);
                for (Violation v : violations) {
                    validationContext.addIssue(PushStepKind.COMMIT_MESSAGE, v.reason(), v.formattedDetail());
                    allViolations.add(v);
                }
            } catch (Exception e) {
                // Fail closed: a validation control that cannot run must block the push, not silently pass.
                log.error("Failed to validate commit messages for {}", cmd.getRefName(), e);
                validationContext.addError(
                        PushStepKind.COMMIT_MESSAGE,
                        "commit message validation could not complete for " + cmd.getRefName(),
                        "Validation error: " + e.getMessage());
                pushContext.addStep(PushStep.builder()
                        .stepName(getStepName())
                        .stepOrder(displayOrder())
                        .status(StepStatus.FAIL)
                        .errorMessage("Validation error: " + e.getMessage())
                        .build());
                hadError = true;
            }
        }

        if (allViolations.isEmpty() && !hadError) {
            pushContext.addStep(PushStep.builder()
                    .stepName(getStepName())
                    .stepOrder(displayOrder())
                    .status(StepStatus.PASS)
                    .build());
        }
    }

    @Override
    public LifecycleStage stage() {
        return LifecycleStage.MANDATORY_PROCESSING;
    }

    @Override
    public String getName() {
        return "CommitMessageValidationHook";
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.COMMIT_MESSAGE);
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
