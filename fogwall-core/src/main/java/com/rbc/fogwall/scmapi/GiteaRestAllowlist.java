package com.rbc.fogwall.scmapi;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed allowlist of the mutating Gitea/Forgejo REST v1 calls made by {@code tea} and {@code fj} — see
 * docs/internals/SCM_API_PROXY.md's Gitea/Forgejo section. Like GitLab and unlike GitHub, the repository is addressed
 * directly in the URL, so there is no opaque-ID resolution step: the authorization target is read straight off the
 * matched path. Gitea splits it into two path segments ({@code /repos/{owner}/{repo}/...}) rather than GitLab's single
 * URL-encoded {@code owner%2Frepo} segment.
 *
 * <p><b>One allowlist covers both CLIs.</b> {@code tea} and {@code fj} talk to the same server API and differ only in
 * which subset of it they exercise, so this table is the union of the two. That matters for coverage, because the two
 * reach the same user-facing operation by different endpoints: {@code tea pr close} sends {@code PATCH /pulls/{n}}
 * while {@code fj pr close} sends {@code PATCH /issues/{n}} (Forgejo models a PR as an issue). Allowlisting only the
 * {@code /pulls} form silently breaks {@code fj}, and vice versa.
 *
 * <p>Routing by {@code User-Agent} to tell the two CLIs apart is deliberately <b>not</b> done: the header is
 * client-controlled, so making it an authorization input would let any caller pick whichever rule set is looser.
 *
 * <p>Deliberately a hardcoded table, not config-driven, for the same reason as {@link GitHubMutationAllowlist} and
 * {@link GitLabRestAllowlist} — this is the security boundary, not an operator knob. Scoped to the
 * contribution-lifecycle matrix (issue/PR create, update, comment, review, merge); label, assignee, tracked-time,
 * dependency and blocking endpoints that the CLIs can also reach are intentionally absent and therefore denied.
 */
public final class GiteaRestAllowlist {

    private record Rule(String method, Pattern pathPattern, String operation) {}

    /** {@code {owner}/{repo}}, each its own path segment — the CLIs URL-encode the two independently. */
    private static final String REPO = "^/repos/([^/]+)/([^/]+)";

    private static final List<Rule> RULES = List.of(
            new Rule("POST", Pattern.compile(REPO + "/issues$"), "issues.create"),
            // Also carries `fj pr close` and `fj issue close` — both send {"state":"closed"} here.
            new Rule("PATCH", Pattern.compile(REPO + "/issues/\\d+$"), "issues.update"),
            // Comments on a PR use the issue path too, in both CLIs — Gitea models a PR as an issue.
            new Rule("POST", Pattern.compile(REPO + "/issues/\\d+/comments$"), "issues.comment"),
            new Rule("PATCH", Pattern.compile(REPO + "/issues/comments/\\d+$"), "issues.comment.update"),
            new Rule("POST", Pattern.compile(REPO + "/pulls$"), "pulls.create"),
            // Also carries `tea pr close`: tea sends a full-object PATCH, so close and edit are the same request
            // shape on the wire and cannot be told apart here. Granularity is method+path, never intent.
            new Rule("PATCH", Pattern.compile(REPO + "/pulls/\\d+$"), "pulls.update"));

    private GiteaRestAllowlist() {}

    /**
     * Matches an incoming {@code method}/{@code path} against the allowlist, where {@code path} is the raw,
     * still-URL-encoded request sub-path below the dialect's {@code /api/v1} mount point (e.g.
     * {@code /repos/acme/widgets/issues}). Returns empty when nothing matches — the caller must fail closed.
     */
    public static Optional<ScmApiRestMatch> match(String method, String path) {
        if (method == null || path == null) return Optional.empty();
        for (Rule rule : RULES) {
            if (!rule.method().equalsIgnoreCase(method)) continue;
            Matcher matcher = rule.pathPattern().matcher(path);
            if (!matcher.matches()) continue;
            String owner = decodeSegment(matcher.group(1));
            String repo = decodeSegment(matcher.group(2));
            if (owner.isEmpty() || repo.isEmpty()) continue;
            return Optional.of(new ScmApiRestMatch(rule.operation(), new OwnerRepo(owner, repo)));
        }
        return Optional.empty();
    }

    private static String decodeSegment(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
