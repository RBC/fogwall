package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.DiffScanConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.CommitInspectionService;
import com.rbc.fogwall.git.DiffGenerationHook;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.BlockedContentDiffCheck;
import com.rbc.fogwall.validation.Violation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;

/**
 * Filter that scans the diff content of incoming pushes for blocked literals and patterns. Runs in the transparent
 * proxy pipeline after {@link EnrichPushCommitsFilter} (which has already cloned the repo and unpacked push objects),
 * so the local repository is available as a cache hit.
 *
 * <p>Only added lines (prefixed with {@code +} in the unified diff, excluding the {@code +++} header) are scanned.
 * Deletions and context lines are ignored.
 *
 * <p>Two scan passes run, mirroring the store-and-forward {@code DiffScanningHook}: an aggregate scan of the old..new
 * diff, and a per-commit scan of every individually introduced commit. The per-commit pass exists so that content added
 * in one commit and removed by a later commit in the same push is still caught — otherwise the aggregate diff alone
 * would look clean (see RBC/fogwall#339).
 *
 * <p>This filter runs at order 300, in the content filters range (200-399).
 */
@Slf4j
public class ScanDiffFilter extends AbstractFogwallFilter {

    private static final int ORDER = 300;

    private final Supplier<DiffScanConfig> diffScanConfigSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public ScanDiffFilter(Supplier<DiffScanConfig> diffScanConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.diffScanConfigSupplier = diffScanConfigSupplier;
    }

    /** Fixed-config constructor. Useful in tests; wraps the value in a constant supplier. */
    public ScanDiffFilter(DiffScanConfig diffScanConfig) {
        this(() -> diffScanConfig != null ? diffScanConfig : DiffScanConfig.defaultConfig());
    }

    @Override
    public String getStepName() {
        return "scanDiff";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        if (requestDetails.isTagPush()) {
            log.debug("Skipping diff generation for tag push: {}", requestDetails.getBranch());
            return;
        }

        String fromCommit = requestDetails.getCommitFrom();
        String toCommit = requestDetails.getCommitTo();
        if (toCommit == null || toCommit.isEmpty()) {
            log.debug("No commit range in request details, skipping diff generation");
            return;
        }

        AtomicBoolean anyFailed = new AtomicBoolean(false);
        BlockedContentDiffCheck check =
                new BlockedContentDiffCheck(diffScanConfigSupplier.get().getBlock());

        try {
            Repository repository = requestDetails.getLocalRepository();
            if (repository == null) {
                log.warn(
                        "localRepository not set on request - EnrichPushCommitsFilter may not have run; skipping diff scan");
                return;
            }

            // Pass 1: aggregate scan of the net old..new diff
            String diff = CommitInspectionService.getFormattedDiff(repository, fromCommit, toCommit);

            // Always record the diff so the dashboard can display it
            PushStep diffStep = PushStep.builder()
                    .pushId(requestDetails.getId().toString())
                    .stepName(DiffGenerationHook.STEP_NAME_PUSH_DIFF)
                    .stepOrder(ORDER - 20)
                    .status(StepStatus.PASS)
                    .content(diff)
                    .build();
            requestDetails.getSteps().add(diffStep);

            List<Violation> violations = check.check(diff).orElse(List.of());
            if (!violations.isEmpty()) {
                log.warn("Diff scan found {} violation(s)", violations.size());
                for (Violation v : violations) {
                    recordIssue(request, v.reason(), v.formattedDetail());
                }
                anyFailed.set(true);
            }

            // Pass 2: per-commit scan — catches content introduced in intermediate commits
            CommitInspectionService.forEachIntroducedCommit(repository, fromCommit, toCommit, (commit, diffs) -> {
                String shortSha = commit.getSha().substring(0, 7);
                String perCommitDiff = CommitInspectionService.formatDiffEntries(repository, diffs);
                List<Violation> perCommitViolations = check.check(perCommitDiff).orElse(List.of());
                if (!perCommitViolations.isEmpty()) {
                    for (Violation v : perCommitViolations) {
                        String reason = shortSha + ": " + v.reason();
                        String detail = "commit " + shortSha + ": " + v.formattedDetail();
                        recordIssue(request, reason, detail);
                    }
                    anyFailed.set(true);
                }
            });

            if (!anyFailed.get()) {
                log.debug("Diff scan passed for {}..{}", fromCommit, toCommit);
                recordStep(request, StepStatus.PASS, "", "");
            }

        } catch (Exception e) {
            log.warn("Skipping diff scan for push {}..{}: {}", fromCommit, toCommit, e.getMessage());
            recordStep(request, StepStatus.SKIPPED, "", e.getMessage());
        }
    }
}
