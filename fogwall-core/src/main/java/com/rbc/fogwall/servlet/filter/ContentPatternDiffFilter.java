package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.CommitInspectionService;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.ContentPatternBundleResolver;
import com.rbc.fogwall.validation.ContentPatternFinding;
import com.rbc.fogwall.validation.PatternBundleScanner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;

/**
 * Proxy-mode content-pattern scanning of the pushed diff (structured PII/identifier detection - SIN, SSN, NINO, etc).
 * Runs after {@link EnrichPushCommitsFilter} (local repository is populated) and mirrors the store-and-forward
 * {@link com.rbc.fogwall.git.ContentPatternDiffHook}.
 *
 * <p>Always WARN, never blocks - see {@link ContentPatternConfig}. Matched values are routed to
 * {@link GitRequestDetails#getSecretsToRedact()} so they never persist unredacted in the stored diff, same as
 * {@link SecretScanningFilter} findings.
 *
 * <p>This filter runs at order 345, in the content filters range (200-399).
 */
@Slf4j
public class ContentPatternDiffFilter extends AbstractFogwallFilter {

    private static final int ORDER = 345;

    private final ContentPatternConfig config;

    public ContentPatternDiffFilter(ContentPatternConfig config) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.config = config != null ? config : ContentPatternConfig.defaultConfig();
    }

    @Override
    public String getStepName() {
        return "scanContentPatternsDiff";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!config.isEnabled() || !config.isScanDiff()) {
            log.debug("Content pattern diff scanning disabled - skipping");
            return;
        }

        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }
        if (requestDetails.isTagPush()) {
            return;
        }

        String fromCommit = requestDetails.getCommitFrom();
        String toCommit = requestDetails.getCommitTo();
        if (toCommit == null || toCommit.isEmpty()) {
            log.debug("No commit range in request details, skipping content pattern scan");
            return;
        }

        Repository repository = requestDetails.getLocalRepository();
        if (repository == null) {
            log.warn(
                    "localRepository not set on request - EnrichPushCommitsFilter may not have run; skipping content pattern scan");
            return;
        }

        try {
            String diff = CommitInspectionService.getFormattedDiff(repository, fromCommit, toCommit);
            var scanner = new PatternBundleScanner(ContentPatternBundleResolver.resolve(config));
            List<ContentPatternFinding> findings = scanner.scan(diff);
            recordFindings(request, requestDetails, findings);
        } catch (Exception e) {
            log.warn("Skipping content pattern scan for push {}..{}: {}", fromCommit, toCommit, e.getMessage());
            recordStep(request, StepStatus.SKIPPED, "", e.getMessage());
        }
    }

    private void recordFindings(
            HttpServletRequest request, GitRequestDetails requestDetails, List<ContentPatternFinding> findings) {
        if (findings.isEmpty()) {
            recordStep(request, StepStatus.PASS, "", "");
            return;
        }

        for (ContentPatternFinding finding : findings) {
            requestDetails.getSecretsToRedact().add(finding.matchedText());
        }

        String content = findings.stream()
                .map(f -> "Possible " + f.dataType() + " (" + f.jurisdiction() + ")")
                .distinct()
                .collect(Collectors.joining("\n"));
        recordStep(request, StepStatus.WARN, null, content);
    }
}
