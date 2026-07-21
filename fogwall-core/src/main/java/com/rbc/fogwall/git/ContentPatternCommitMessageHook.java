package com.rbc.fogwall.git;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.ContentPatternBundleResolver;
import com.rbc.fogwall.validation.ContentPatternFinding;
import com.rbc.fogwall.validation.PatternBundleScanner;
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
 * S&amp;F-mode content-pattern scanning of commit messages (structured PII/identifier detection - SIN, SSN, NINO, etc).
 * A human describing an incident or bug in a commit message is a real, separate way PII ends up in a repo, distinct
 * from the diff content itself - see {@link ContentPatternDiffHook}.
 *
 * <p>Always WARN, never blocks - see {@link ContentPatternConfig}.
 */
@Slf4j
@RequiredArgsConstructor
public class ContentPatternCommitMessageHook implements FogwallHook {

    private static final int ORDER = 265;
    static final String STEP_NAME = "scanContentPatternsMessages";

    private final ContentPatternConfig config;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (!config.isEnabled() || !config.isScanCommitMessages()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.SKIPPED)
                    .build());
            return;
        }

        var scanner = new PatternBundleScanner(ContentPatternBundleResolver.resolve(config));
        List<ContentPatternFinding> allFindings = new ArrayList<>();
        Repository repo = rp.getRepository();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) {
                continue;
            }
            try {
                for (Commit commit : getCommits(repo, cmd)) {
                    allFindings.addAll(scanner.scan(commit.getMessage()));
                }
            } catch (Exception e) {
                // Fail-open per commit - a WARN-only visibility check should never itself block the push.
                log.error("Failed to scan commit messages for content patterns on {}", cmd.getRefName(), e);
            }
        }

        ContentPatternStepRecorder.record(pushContext, STEP_NAME, ORDER, allFindings);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "ContentPatternCommitMessageHook";
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
