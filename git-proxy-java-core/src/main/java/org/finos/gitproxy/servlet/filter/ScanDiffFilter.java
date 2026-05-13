package org.finos.gitproxy.servlet.filter;

import static org.finos.gitproxy.servlet.GitProxyServlet.GIT_REQUEST_ATTR;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.finos.gitproxy.config.DiffScanConfig;
import org.finos.gitproxy.db.model.PushStep;
import org.finos.gitproxy.db.model.StepStatus;
import org.finos.gitproxy.git.CommitInspectionService;
import org.finos.gitproxy.git.DiffGenerationHook;
import org.finos.gitproxy.git.GitRequestDetails;
import org.finos.gitproxy.git.HttpOperation;
import org.finos.gitproxy.provider.GitProxyProvider;
import org.finos.gitproxy.validation.BlockedContentDiffCheck;
import org.finos.gitproxy.validation.Violation;

/**
 * Filter that scans the diff content of incoming pushes for blocked literals and patterns. Runs in the transparent
 * proxy pipeline after {@link EnrichPushCommitsFilter} (which has already cloned the repo and unpacked push objects),
 * so the local repository is available as a cache hit.
 *
 * <p>Only added lines (prefixed with {@code +} in the unified diff, excluding the {@code +++} header) are scanned.
 * Deletions and context lines are ignored.
 *
 * <p>This filter runs at order 300, in the content filters range (200-399).
 */
@Slf4j
public class ScanDiffFilter extends AbstractProviderAwareGitProxyFilter {

    private static final int ORDER = 300;
    private static final String PROXY_PATH_PREFIX = "/proxy";

    private final Supplier<DiffScanConfig> diffScanConfigSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public ScanDiffFilter(GitProxyProvider provider, Supplier<DiffScanConfig> diffScanConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH), provider, PROXY_PATH_PREFIX);
        this.diffScanConfigSupplier = diffScanConfigSupplier;
    }

    /** Fixed-config constructor. Useful in tests; wraps the value in a constant supplier. */
    public ScanDiffFilter(GitProxyProvider provider, DiffScanConfig diffScanConfig) {
        this(provider, () -> diffScanConfig != null ? diffScanConfig : DiffScanConfig.defaultConfig());
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

        try {
            Repository repository = requestDetails.getLocalRepository();
            if (repository == null) {
                log.warn(
                        "localRepository not set on request - EnrichPushCommitsFilter may not have run; skipping diff scan");
                return;
            }

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

            BlockedContentDiffCheck check =
                    new BlockedContentDiffCheck(diffScanConfigSupplier.get().getBlock());
            List<Violation> violations = check.check(diff).orElse(List.of());

            if (!violations.isEmpty()) {
                log.warn("Diff scan found {} violation(s)", violations.size());
                for (Violation v : violations) {
                    recordIssue(request, v.reason(), v.formattedDetail());
                }
            } else {
                log.debug("Diff scan passed for {}..{}", fromCommit, toCommit);
                recordStep(request, StepStatus.PASS, "", "");
            }

        } catch (Exception e) {
            log.warn("Skipping diff scan for push {}..{}: {}", fromCommit, toCommit, e.getMessage());
            recordStep(request, StepStatus.SKIPPED, "", e.getMessage());
        }
    }
}
