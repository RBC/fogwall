package com.rbc.fogwall.scmapi;

import java.util.Set;

/**
 * Fixed, hardcoded allowlist of GitHub GraphQL mutation fields the SCM API proxy forwards. Anything not in this set is
 * denied — see docs/internals/SCM_API_PROXY.md's "GitHub allowlist" section for the source list and rationale.
 *
 * <p>Deliberately not config-driven: this list <em>is</em> the security boundary, not an operator knob to tune.
 */
public final class GitHubMutationAllowlist {

    private static final Set<String> ALLOWED_MUTATION_FIELDS = Set.of(
            "createIssue",
            "updateIssue",
            "closeIssue",
            "createPullRequest",
            "updatePullRequest",
            "closePullRequest",
            "addComment");

    private GitHubMutationAllowlist() {}

    public static boolean isAllowed(String mutationField) {
        return ALLOWED_MUTATION_FIELDS.contains(mutationField);
    }
}
