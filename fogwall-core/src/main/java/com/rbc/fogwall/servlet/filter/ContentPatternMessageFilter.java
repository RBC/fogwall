package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.ContentPatternBundleResolver;
import com.rbc.fogwall.validation.ContentPatternFinding;
import com.rbc.fogwall.validation.PatternBundleScanner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy-mode content-pattern scanning of commit messages (structured PII/identifier detection - SIN, SSN, NINO, etc),
 * mirroring the store-and-forward {@link com.rbc.fogwall.git.ContentPatternCommitMessageHook}.
 *
 * <p>Always WARN, never blocks - see {@link ContentPatternConfig}.
 *
 * <p>This filter runs at order 265, in the content filters range (200-399).
 */
@Slf4j
public class ContentPatternMessageFilter extends AbstractFogwallFilter {

    private static final int ORDER = 265;

    private final ContentPatternConfig config;

    public ContentPatternMessageFilter(ContentPatternConfig config) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.config = config != null ? config : ContentPatternConfig.defaultConfig();
    }

    @Override
    public String getStepName() {
        return "scanContentPatternsMessages";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!config.isEnabled() || !config.isScanCommitMessages()) {
            log.debug("Content pattern message scanning disabled - skipping");
            return;
        }

        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        var commits = requestDetails.getPushedCommits();
        if (commits == null || commits.isEmpty()) {
            log.debug("No commits to scan");
            return;
        }

        var scanner = new PatternBundleScanner(ContentPatternBundleResolver.resolve(config));
        List<ContentPatternFinding> findings = new ArrayList<>();
        commits.forEach(commit -> findings.addAll(scanner.scan(commit.getMessage())));

        if (findings.isEmpty()) {
            recordStep(request, StepStatus.PASS, "", "");
            return;
        }

        String content = findings.stream()
                .map(f -> "Possible " + f.dataType() + " (" + f.jurisdiction() + ")")
                .distinct()
                .collect(Collectors.joining("\n"));
        recordStep(request, StepStatus.WARN, null, content);
    }
}
