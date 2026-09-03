package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.validation.CommitMessageCheck;
import com.rbc.fogwall.validation.Violation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy-mode adapter for {@link CommitMessageCheck}. Reads commits from {@link GitRequestDetails} and translates
 * violations into filter-chain rejections.
 *
 * <p>This filter runs at order 260, which is in the content filters range (200-399).
 */
@Slf4j
public class CheckCommitMessagesFilter extends AbstractFogwallFilter {

    private static final int ORDER = 260;
    private final Supplier<CommitConfig> commitConfigSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public CheckCommitMessagesFilter(Supplier<CommitConfig> commitConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.commitConfigSupplier = commitConfigSupplier;
    }

    /** Fixed-config constructor. Useful in tests; wraps the value in a constant supplier. */
    public CheckCommitMessagesFilter(CommitConfig commitConfig) {
        this(() -> commitConfig != null ? commitConfig : CommitConfig.defaultConfig());
    }

    @Override
    public String getStepName() {
        return "checkCommitMessages";
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        // An annotated tag's own message is developer-authored free text like a commit message, but the tag's target
        // commit was already validated on its branch push, so it never enters pushedCommits. Validate it here through
        // the same CommitMessageCheck so a rule added for commits automatically covers tags too (#474).
        List<Commit> pushed = requestDetails.getPushedCommits();
        List<Commit> commits = new ArrayList<>(pushed != null ? pushed : List.of());
        String tagMessage = requestDetails.getTagMessage();
        if (tagMessage != null && !tagMessage.isBlank()) {
            commits.add(Commit.builder().message(tagMessage).build());
        }
        if (commits.isEmpty()) {
            log.debug("No commit or tag messages to validate");
            return;
        }

        List<Violation> violations = new CommitMessageCheck(commitConfigSupplier.get()).check(commits);
        if (violations.isEmpty()) {
            log.debug("All commit messages passed");
            return;
        }

        log.warn("Commit message check failed: {} violation(s)", violations.size());
        for (Violation v : violations) {
            recordIssue(request, v.reason(), v.formattedDetail());
        }
    }
}
