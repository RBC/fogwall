package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
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
 * Pre-receive hook that rejects pushes where no commits can be found in the pushed range. Two cases are distinguished:
 *
 * <ul>
 *   <li><b>New branch with no new commits</b> - the branch tip resolves to an existing commit already reachable from
 *       another ref; the developer pushed an empty branch pointer with no new work.
 *   <li><b>Existing branch with no new commits</b> - commit data could not be resolved; this usually indicates a proxy
 *       configuration or repository state problem.
 * </ul>
 *
 * <p>Terminating: an empty branch leaves nothing for the content stages to inspect. The hook records the issue and the
 * chain runner ends the chain.
 */
@Slf4j
@RequiredArgsConstructor
public final class CheckEmptyBranchHook implements MandatoryFogwallHook {

    private final ValidationContext validationContext;
    private final PushContext pushContext;

    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Repository repo = rp.getRepository();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) continue;
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            // Tags legitimately point to existing commits — skip the "empty branch" check
            if (cmd.getRefName().startsWith("refs/tags/")) continue;

            try {
                List<Commit> commits = getCommits(repo, cmd);
                if (!commits.isEmpty()) continue;

                String msg;
                if (ObjectId.zeroId().equals(cmd.getOldId())) {
                    msg = "Push blocked: Empty branch. Please make a commit before pushing a new branch.";
                } else {
                    msg = "Push blocked: Commit data not found. Please contact an administrator for support.";
                }

                rp.sendMessage(color(RED, "" + sym(NO_ENTRY) + "  " + msg));
                validationContext.addIssue(PushStepKind.EMPTY_BRANCH, msg, msg);
                // Declared terminating: the chain runner rejects the commands and stops.
                return;

            } catch (Exception e) {
                log.error("Failed to check empty branch for {}", cmd.getRefName(), e);
            }
        }

        if (pushContext != null) {
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
    public boolean terminatesChainOnFailure() {
        return true;
    }

    @Override
    public String getName() {
        return "CheckEmptyBranchHook";
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.EMPTY_BRANCH);
    }

    private List<Commit> getCommits(Repository repo, ReceiveCommand cmd) throws Exception {
        return CommitInspectionService.getCommitRange(
                repo, cmd.getOldId().name(), cmd.getNewId().name());
    }
}
