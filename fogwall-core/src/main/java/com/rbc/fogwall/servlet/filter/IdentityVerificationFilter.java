package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.*;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Transparent-proxy adapter for commit identity verification. Mirrors {@link IdentityVerificationHook} for the
 * transparent proxy pipeline.
 *
 * <p>Checks that every pushed commit's author and committer email is registered to the authenticated push user.
 * Behaviour is controlled by {@link CommitConfig#getIdentityVerification()}:
 *
 * <ul>
 *   <li>{@code STRICT} — records an issue via {@link #recordIssue} so {@link ValidationSummaryFilter} blocks the push.
 *   <li>{@code WARN} — logs warnings but allows the push through (default).
 *   <li>{@code OFF} — skips the check entirely.
 * </ul>
 *
 * <p>Runs at order 160, after {@link CheckUserPushPermissionFilter} (150) and before content-validation filters (200+).
 */
@Slf4j
public class IdentityVerificationFilter extends AbstractFogwallFilter {

    private static final int ORDER = 160;
    private static final String STEP_NAME = "identityVerification";

    private final PushIdentityResolver identityResolver;
    private final Supplier<CommitConfig.IdentityVerificationConfig> configSupplier;

    /** Live-reload constructor — config is read from the supplier on every request. */
    public IdentityVerificationFilter(
            PushIdentityResolver identityResolver, Supplier<CommitConfig> commitConfigSupplier) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.identityResolver = identityResolver;
        this.configSupplier = () -> {
            CommitConfig.IdentityVerificationConfig c =
                    commitConfigSupplier.get().getIdentityVerification();
            return c != null
                    ? c
                    : CommitConfig.IdentityVerificationConfig.builder().build();
        };
    }

    /** Fixed-config constructor. Useful in tests. */
    public IdentityVerificationFilter(
            PushIdentityResolver identityResolver, CommitConfig.IdentityVerificationConfig config) {
        super(ORDER, Set.of(HttpOperation.PUSH));
        this.identityResolver = identityResolver;
        this.configSupplier = () -> config != null
                ? config
                : CommitConfig.IdentityVerificationConfig.builder().build();
    }

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var config = configSupplier.get();
        if (config.isEffectivelyOff()) {
            log.debug("Identity verification disabled (committer=off, author=off)");
            return;
        }

        if (identityResolver == null) {
            log.debug("No identity resolver configured — skipping identity verification (open mode)");
            return;
        }

        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        List<Commit> commits = requestDetails.getPushedCommits();
        if (commits == null || commits.isEmpty()) {
            log.debug("No commits to verify");
            return;
        }

        String[] userPass = extractBasicAuth(request);
        String pushUsername = userPass != null ? userPass[0] : null;
        String pushToken = userPass != null ? userPass[1] : null;

        Optional<UserEntry> resolved = identityResolver.resolve(requestDetails.getProvider(), pushUsername, pushToken);
        if (resolved.isEmpty()) {
            log.debug("Push user '{}' could not be resolved — skipping identity verification", pushUsername);
            return;
        }

        UserEntry user = resolved.get();
        List<String> registeredEmails = user.getEmails() != null ? user.getEmails() : List.of();
        List<String> blockingViolations = new ArrayList<>();
        List<String> warnViolations = new ArrayList<>();

        for (Commit commit : commits) {
            String sha = abbrev(commit.getSha());

            if (config.getCommitter() != CommitConfig.IdentityVerificationMode.OFF && commit.getCommitter() != null) {
                String email = commit.getCommitter().getEmail();
                if (email != null && !registeredEmails.contains(email)) {
                    String msg = sym(CROSS_MARK) + "  Unrecognised committer email: <" + email + "> (commit " + sha
                            + ") — not in proxy user registry";
                    if (config.getCommitter() == CommitConfig.IdentityVerificationMode.STRICT) {
                        blockingViolations.add(msg);
                    } else {
                        warnViolations.add(msg);
                    }
                }
            }

            if (config.getAuthor() != CommitConfig.IdentityVerificationMode.OFF && commit.getAuthor() != null) {
                String email = commit.getAuthor().getEmail();
                if (email != null && !registeredEmails.contains(email)) {
                    String msg = sym(CROSS_MARK) + "  Unrecognised author email: <" + email + "> (commit " + sha
                            + ") — not in proxy user registry";
                    if (config.getAuthor() == CommitConfig.IdentityVerificationMode.STRICT) {
                        blockingViolations.add(msg);
                    } else {
                        warnViolations.add(msg);
                    }
                }
            }
        }

        if (blockingViolations.isEmpty() && warnViolations.isEmpty()) {
            log.debug("Identity verification passed for push user '{}'", user.getUsername());
            return;
        }

        if (!blockingViolations.isEmpty()) {
            List<String> allViolations = new ArrayList<>(blockingViolations);
            allViolations.addAll(warnViolations);
            log.warn(
                    "Identity verification failed for push user '{}': {} violation(s)",
                    user.getUsername(),
                    allViolations.size());
            String detail = GitClientUtils.format(
                    sym(NO_ENTRY) + "  Push Blocked — Commit Identity Mismatch",
                    String.join("\n", allViolations),
                    RED,
                    null);
            recordIssue(request, "Commit identity does not match push user " + user.getUsername(), detail);
        } else {
            log.warn(
                    "Identity verification warnings for push user '{}': {} mismatch(es)",
                    user.getUsername(),
                    warnViolations.size());
            recordStep(
                    request,
                    StepStatus.WARN,
                    null,
                    warnViolations.size() + " unrecognised commit email(s) — not in proxy user registry");
        }
    }

    private static String abbrev(String sha) {
        if (sha == null) return "?";
        return sha.substring(0, Math.min(7, sha.length()));
    }

    private static String[] extractBasicAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) return null;
        try {
            String decoded = new String(Base64.getDecoder()
                    .decode(authHeader.substring("Basic ".length()).trim()));
            int colon = decoded.indexOf(':');
            if (colon < 0) return null;
            return new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
