package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.GpgSignatureCheck;
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
 * server-mode adapter for {@link GpgSignatureCheck}. Reads commits from the JGit repository, sends per-violation
 * sideband feedback, and records results in the shared {@link ValidationContext} and {@link PushContext}.
 */
@Slf4j
@RequiredArgsConstructor
public final class GpgSignatureHook implements MandatoryFogwallHook {

    private final GpgConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        var check = new GpgSignatureCheck(config);
        Repository repo = rp.getRepository();
        List<Violation> allViolations = new ArrayList<>();
        boolean hadError = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                List<Violation> violations = check.check(getCommits(repo, cmd));
                for (Violation v : violations) {
                    rp.sendMessage(color(RED, "" + sym(CROSS_MARK) + "  " + v.subject() + " - " + v.reason()));
                    validationContext.addIssue(PushStepKind.GPG_SIGNATURE, v.reason(), v.formattedDetail());
                    allViolations.add(v);
                }
            } catch (Exception e) {
                // Fail closed: a signature control that cannot run must block the push, not silently pass.
                log.error("Failed to check GPG signatures for {}", cmd.getRefName(), e);
                rp.sendMessage(color(YELLOW, "" + sym(WARNING) + "  Could not check GPG signature: " + e.getMessage()));
                validationContext.addError(
                        PushStepKind.GPG_SIGNATURE,
                        "GPG signature verification could not complete for " + cmd.getRefName(),
                        "Signature check error: " + e.getMessage());
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
        return "GpgSignatureHook";
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.GPG_SIGNATURE);
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
