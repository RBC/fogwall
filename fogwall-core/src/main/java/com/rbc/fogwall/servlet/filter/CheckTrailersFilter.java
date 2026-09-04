package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.TrailerPolicyCheck;
import com.rbc.fogwall.validation.Violation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy-mode adapter for {@link TrailerPolicyCheck} (fogwall#146). Reads commits from {@link GitRequestDetails} and
 * translates blocking trailer-policy violations into filter-chain rejections. Runs at order 255, in the content filters
 * range (200-399).
 */
@Slf4j
public class CheckTrailersFilter extends AbstractFogwallFilter {

    private static final int ORDER = 255;
    private final Supplier<CommitConfig> commitConfigSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public CheckTrailersFilter(Supplier<CommitConfig> commitConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.commitConfigSupplier = commitConfigSupplier;
    }

    /** Fixed-config constructor. Useful in tests; wraps the value in a constant supplier. */
    public CheckTrailersFilter(CommitConfig commitConfig) {
        this(() -> commitConfig != null ? commitConfig : CommitConfig.defaultConfig());
    }

    @Override
    public String getStepName() {
        return "checkTrailers";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        var commits = requestDetails.getPushedCommits();
        if (commits == null || commits.isEmpty()) {
            log.debug("No commits to validate trailers on");
            return;
        }

        List<Violation> violations = new TrailerPolicyCheck(commitConfigSupplier.get()).check(commits);
        if (violations.isEmpty()) {
            log.debug("All commit trailers passed policy");
            return;
        }

        log.warn("Commit trailer policy failed: {} violation(s)", violations.size());
        // Report all violations together so the developer sees everything in one push attempt.
        for (Violation v : violations) {
            recordIssue(request, v.reason(), v.formattedDetail());
        }
    }
}
