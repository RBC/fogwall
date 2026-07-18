package com.rbc.fogwall.git;

import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.BinaryBlobCheck;
import com.rbc.fogwall.validation.Violation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Pre-receive hook that flags added/modified blobs in a push that exceed a configured size threshold or match a denied
 * MIME type. See {@link BinaryBlobCheck} for the detection logic — JGit reports object type and size from pack data, so
 * blob content is never fully loaded.
 *
 * <p>Two scan passes run per ref, mirroring {@link DiffScanningHook}: an aggregate scan of the old..new diff, and a
 * per-commit scan of every individually introduced commit. The per-commit pass exists so that a binary blob added in
 * one commit and removed by a later commit in the same push is still caught — otherwise the aggregate diff alone would
 * look clean (see RBC/fogwall#339 for the equivalent diff-scan bypass this closes). Skips deletions and tag pushes.
 */
@Slf4j
@RequiredArgsConstructor
public class BinaryBlobDetectionHook implements FogwallHook {

    private static final int ORDER = 290;
    private static final String STEP_NAME = "binaryBlob";

    private final BinaryBlobConfig binaryBlobConfig;
    private final ValidationContext validationContext;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (!binaryBlobConfig.isEnabled()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.SKIPPED)
                    .build());
            return;
        }

        Repository repo = rp.getRepository();
        BinaryBlobCheck check = new BinaryBlobCheck(binaryBlobConfig);
        List<String> logs = new ArrayList<>();
        AtomicBoolean anyFailed = new AtomicBoolean(false);

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) {
                continue;
            }
            if (cmd.getRefName().startsWith("refs/tags/")) {
                continue;
            }

            String effectiveFrom = pushContext.getEffectiveFromId(cmd.getRefName());
            boolean hasNonZeroEffectiveBase = effectiveFrom != null && !effectiveFrom.matches("^0+$");
            String baseCommit =
                    hasNonZeroEffectiveBase ? effectiveFrom : cmd.getOldId().name();
            String tipCommit = cmd.getNewId().name();

            // Pass 1: aggregate scan of the net old..new diff
            try {
                List<DiffEntry> diffs = CommitInspectionService.getDiff(repo, baseCommit, tipCommit);
                List<Violation> violations = check.check(repo, diffs);
                if (violations.isEmpty()) {
                    logs.add("PASS: aggregate: " + cmd.getRefName());
                } else {
                    for (Violation v : violations) {
                        validationContext.addIssue(STEP_NAME, v.reason(), v.formattedDetail());
                        logs.add("FAIL: aggregate: " + v.reason());
                    }
                    anyFailed.set(true);
                }
            } catch (IOException e) {
                log.warn("Binary blob detection failed for {}, skipping", cmd.getRefName(), e);
                logs.add("WARN: " + cmd.getRefName() + " - diff failed, skipped");
            }

            // Pass 2: per-commit scan — catches blobs introduced in intermediate commits
            try {
                CommitInspectionService.forEachIntroducedCommit(repo, baseCommit, tipCommit, (commit, diffs) -> {
                    String shortSha = commit.getSha().substring(0, 7);
                    List<Violation> violations = check.check(repo, diffs);
                    if (violations.isEmpty()) {
                        logs.add("PASS: " + shortSha);
                    } else {
                        for (Violation v : violations) {
                            String reason = shortSha + ": " + v.reason();
                            String detail = "commit " + shortSha + ": " + v.formattedDetail();
                            validationContext.addIssue(STEP_NAME, reason, detail);
                            logs.add("FAIL: " + reason);
                        }
                        anyFailed.set(true);
                    }
                });
            } catch (IOException | GitAPIException e) {
                log.warn("Could not enumerate commits for per-commit binary blob scan on {}", cmd.getRefName(), e);
            }
        }

        if (!anyFailed.get()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.PASS)
                    .logs(logs)
                    .build());
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "BinaryBlobDetectionHook";
    }
}
