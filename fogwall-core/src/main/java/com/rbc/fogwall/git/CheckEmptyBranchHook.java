package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Pre-receive hook that records whether each pushed ref introduces new commits, and blocks a push whose commits cannot
 * be read.
 *
 * <ul>
 *   <li><b>No new commits</b> - the pushed tip is already reachable from an existing ref, as when a branch is created
 *       at a commit already upstream or moved back to an older one. There is no new content to inspect, so the push
 *       continues and the step names the ref.
 *   <li><b>Commit data not found</b> - the pushed range could not be walked. The content stages would have nothing to
 *       inspect for a reason other than there being nothing new, so the push is blocked.
 * </ul>
 *
 * <p>Terminating: a push whose commits cannot be read leaves nothing for the content stages to inspect. The hook
 * records the issue and the chain runner ends the chain.
 */
@Slf4j
@RequiredArgsConstructor
public final class CheckEmptyBranchHook implements MandatoryFogwallHook {

    private final ValidationContext validationContext;
    private final PushContext pushContext;

    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Repository repo = rp.getRepository();
        List<String> logs = new ArrayList<>();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) continue;
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            // Tags legitimately point to existing commits — skip the check
            if (cmd.getRefName().startsWith("refs/tags/")) continue;

            try {
                if (getCommits(repo, cmd).isEmpty()) {
                    logs.add(cmd.getRefName() + " introduces no new commits");
                }
            } catch (Exception e) {
                log.error("Failed to read the commits pushed to {}", cmd.getRefName(), e);
                String msg = "Push blocked: Commit data not found. Please contact an administrator for support.";
                rp.sendMessage(color(RED, "" + sym(NO_ENTRY) + "  " + msg));
                validationContext.addIssue(PushStepKind.EMPTY_BRANCH, msg, msg);
                // Declared terminating: the chain runner rejects the commands and stops.
                return;
            }
        }

        if (pushContext != null) {
            pushContext.addStep(PushStep.builder()
                    .stepName(getStepName())
                    .stepOrder(displayOrder())
                    .status(StepStatus.PASS)
                    .logs(logs)
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
