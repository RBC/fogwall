package com.rbc.fogwall.validation;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.CommitConfig.CoAuthorPolicy;
import com.rbc.fogwall.git.Commit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Enforces the commit-trailer policy (fogwall#146): DCO {@code Signed-off-by} sign-off and {@code Co-authored-by}
 * attribution rules. Pure, transport-independent {@link CommitCheck} — the same instance backs both the server-mode
 * hook chain and the transparent-proxy filter chain.
 *
 * <p>Both controls are enforce-or-off: a configured rule that a commit violates produces a blocking {@link Violation};
 * an unconfigured rule is skipped. Trailers are self-contained in the commit message, so this check needs no identity
 * registry lookup — the DCO author-match rule compares the {@code Signed-off-by} email to the commit's <em>own</em>
 * author email.
 *
 * <ul>
 *   <li><b>DCO</b> ({@code trailers.signed-off-by.require}) — every commit must carry a {@code Signed-off-by} trailer;
 *       with {@code require-author-match}, one whose email equals the commit author's.
 *   <li><b>Co-author</b> ({@code trailers.co-authored-by.policy}) — {@code BAN} rejects any co-author, {@code REQUIRE}
 *       demands one, {@code ALLOWLIST} rejects a co-author whose email fails the configured domain-allow/local-block
 *       filter.
 * </ul>
 */
@RequiredArgsConstructor
public class TrailerPolicyCheck implements CommitCheck {

    private final CommitConfig config;

    @Override
    public List<Violation> check(List<Commit> commits) {
        CommitConfig.TrailerPolicyConfig policy = config.getTrailers();
        if (policy == null || policy.isEffectivelyOff()) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        for (Commit commit : commits) {
            checkSignedOffBy(commit, policy.getSignedOffBy(), violations);
            checkCoAuthoredBy(commit, policy.getCoAuthoredBy(), violations);
        }
        return violations;
    }

    private void checkSignedOffBy(Commit commit, CommitConfig.SignedOffByConfig sob, List<Violation> violations) {
        if (!sob.isRequire()) {
            return;
        }
        String shortSha = abbrev(commit.getSha());
        List<String> trailers = commit.getSignedOffBy() != null ? commit.getSignedOffBy() : List.of();

        if (trailers.isEmpty()) {
            String detail = sym(CROSS_MARK) + "  commit " + shortSha + " has no Signed-off-by trailer\n"
                    + "  → This repository requires the Developer Certificate of Origin (DCO) sign-off.\n"
                    + "  → Fix: re-commit with sign-off, e.g. git commit --amend --signoff (or git rebase"
                    + " --signoff <base> for a range).";
            violations.add(
                    new Violation("signed-off-by:" + shortSha, "missing Signed-off-by (" + shortSha + ")", detail));
            return;
        }

        if (sob.isRequireAuthorMatch()) {
            String authorEmail = commit.getAuthor() != null ? commit.getAuthor().getEmail() : null;
            boolean matched = authorEmail != null
                    && trailers.stream().anyMatch(t -> authorEmail.equalsIgnoreCase(extractEmail(t)));
            if (!matched) {
                String detail = sym(CROSS_MARK) + "  commit " + shortSha + " has no Signed-off-by matching its author <"
                        + authorEmail + ">\n"
                        + "  → The DCO requires you to sign off your own work: a Signed-off-by whose email equals"
                        + " the commit author.\n"
                        + "  → Fix: git config user.email to your author email, then git commit --amend --signoff.";
                violations.add(new Violation(
                        "signed-off-by:" + shortSha, "Signed-off-by does not match author (" + shortSha + ")", detail));
            }
        }
    }

    private void checkCoAuthoredBy(
            Commit commit, CommitConfig.CoAuthoredByConfig coAuthored, List<Violation> violations) {
        CoAuthorPolicy policy = coAuthored.getPolicy();
        if (policy == CoAuthorPolicy.OFF) {
            return;
        }
        String shortSha = abbrev(commit.getSha());
        List<String> trailers = commit.getCoAuthoredBy() != null ? commit.getCoAuthoredBy() : List.of();

        switch (policy) {
            case BAN -> {
                if (!trailers.isEmpty()) {
                    String detail = sym(CROSS_MARK) + "  commit " + shortSha + " carries a Co-authored-by trailer: "
                            + String.join(", ", trailers) + "\n"
                            + "  → Co-authored-by trailers are not permitted by policy in this repository.\n"
                            + "  → Fix: remove the Co-authored-by line(s) from the commit message"
                            + " (git commit --amend).";
                    violations.add(new Violation(
                            "co-authored-by:" + shortSha, "Co-authored-by not permitted (" + shortSha + ")", detail));
                }
            }
            case REQUIRE -> {
                if (trailers.isEmpty()) {
                    String detail = sym(CROSS_MARK) + "  commit " + shortSha + " has no Co-authored-by trailer\n"
                            + "  → This repository requires a Co-authored-by attribution trailer on every commit.\n"
                            + "  → Fix: add a Co-authored-by: Name <email> line to the commit message"
                            + " (git commit --amend).";
                    violations.add(new Violation(
                            "co-authored-by:" + shortSha, "missing Co-authored-by (" + shortSha + ")", detail));
                }
            }
            case ALLOWLIST -> {
                for (String trailer : trailers) {
                    String email = extractEmail(trailer);
                    String reason = coAuthored.getEmail().violationReason(email);
                    if (reason != null) {
                        String detail = sym(CROSS_MARK) + "  commit " + shortSha + " Co-authored-by (" + trailer
                                + "): " + reason + "\n"
                                + "  → Co-authors must be permitted by policy (allowed domain / not a blocked"
                                + " address).\n"
                                + "  → Fix: remove the disallowed Co-authored-by line, or use an approved"
                                + " co-author identity.";
                        violations.add(new Violation(
                                "co-authored-by:" + shortSha, "Co-authored-by not allowed (" + shortSha + ")", detail));
                    }
                }
            }
            default -> {
                // OFF handled above.
            }
        }
    }

    /**
     * Extract the {@code <email>} from a trailer value of the form {@code Name <email>}. Returns the whole value when
     * it carries no angle brackets, so a bare address still matches.
     */
    static String extractEmail(String trailerValue) {
        if (trailerValue == null) return null;
        int lt = trailerValue.indexOf('<');
        int gt = trailerValue.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            return trailerValue.substring(lt + 1, gt).trim();
        }
        return trailerValue.trim();
    }

    private static String abbrev(String sha) {
        if (sha == null) return "?";
        return sha.substring(0, Math.min(7, sha.length()));
    }
}
