package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.BinaryBlobConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.CommitInspectionService;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.BinaryBlobCheck;
import com.rbc.fogwall.validation.Violation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;

/**
 * Filter that flags added/modified blobs in an incoming push that exceed a configured size threshold or match a denied
 * MIME type. Runs in the transparent proxy pipeline after {@link EnrichPushCommitsFilter} (which has already cloned the
 * repo and unpacked push objects), so the local repository is available as a cache hit.
 *
 * <p>Two scan passes run, mirroring {@link ScanDiffFilter} and the store-and-forward {@code BinaryBlobDetectionHook}:
 * an aggregate scan of the old..new diff, and a per-commit scan of every individually introduced commit. The per-commit
 * pass exists so that a binary blob added in one commit and removed by a later commit in the same push is still caught
 * — otherwise the aggregate diff alone would look clean (see RBC/fogwall#339). See {@link BinaryBlobCheck} for the
 * detection logic.
 *
 * <p>This filter runs at order 290, in the content filters range (200-399) — just before {@link ScanDiffFilter}.
 */
@Slf4j
public class BinaryBlobFilter extends AbstractFogwallFilter {

    private static final int ORDER = 290;

    private final Supplier<BinaryBlobConfig> binaryBlobConfigSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public BinaryBlobFilter(Supplier<BinaryBlobConfig> binaryBlobConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.binaryBlobConfigSupplier = binaryBlobConfigSupplier;
    }

    /** Fixed-config constructor. Useful in tests; wraps the value in a constant supplier. */
    public BinaryBlobFilter(BinaryBlobConfig binaryBlobConfig) {
        this(() -> binaryBlobConfig != null ? binaryBlobConfig : BinaryBlobConfig.defaultConfig());
    }

    @Override
    public String getStepName() {
        return "binaryBlob";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        BinaryBlobConfig config = binaryBlobConfigSupplier.get();
        if (!config.isEnabled()) {
            return;
        }

        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        if (requestDetails.isTagPush()) {
            log.debug("Skipping binary blob detection for tag push: {}", requestDetails.getBranch());
            return;
        }

        String fromCommit = requestDetails.getCommitFrom();
        String toCommit = requestDetails.getCommitTo();
        if (toCommit == null || toCommit.isEmpty()) {
            log.debug("No commit range in request details, skipping binary blob detection");
            return;
        }

        BinaryBlobCheck check = new BinaryBlobCheck(config);
        AtomicBoolean anyFailed = new AtomicBoolean(false);

        try {
            Repository repository = requestDetails.getLocalRepository();
            if (repository == null) {
                log.warn(
                        "localRepository not set on request - EnrichPushCommitsFilter may not have run; skipping binary blob detection");
                return;
            }

            // Pass 1: aggregate scan of the net old..new diff
            List<DiffEntry> diffs = CommitInspectionService.getDiff(repository, fromCommit, toCommit);
            List<Violation> violations = check.check(repository, diffs);
            if (!violations.isEmpty()) {
                log.warn("Binary blob detection found {} violation(s)", violations.size());
                for (Violation v : violations) {
                    recordIssue(request, v.reason(), v.formattedDetail());
                }
                anyFailed.set(true);
            }

            // Pass 2: per-commit scan — catches blobs introduced in intermediate commits
            CommitInspectionService.forEachIntroducedCommit(repository, fromCommit, toCommit, (commit, commitDiffs) -> {
                String shortSha = commit.getSha().substring(0, 7);
                List<Violation> perCommitViolations = check.check(repository, commitDiffs);
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
                log.debug("Binary blob detection passed for {}..{}", fromCommit, toCommit);
                recordStep(request, StepStatus.PASS, "", "");
            }
        } catch (Exception e) {
            log.warn("Skipping binary blob detection for push {}..{}: {}", fromCommit, toCommit, e.getMessage());
            recordStep(request, StepStatus.SKIPPED, "", e.getMessage());
        }
    }
}
