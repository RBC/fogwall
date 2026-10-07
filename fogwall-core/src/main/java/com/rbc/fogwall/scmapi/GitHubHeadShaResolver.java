package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.GitHubProvider;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves a pull request's head commit SHA, for {@code providers.github.scm-api.require-validated-head} (see
 * {@link HeadCommitValidator}) — either a {@code createPullRequest} mutation's {@code input.headRefName}, or (via
 * {@link #resolvePullRequestHeadSha}) a {@code mergePullRequest} mutation's target node directly, since that mutation's
 * input carries no head ref or SHA of its own.
 *
 * <p>{@code input.repositoryId} always names the base repository — the one a fork PR is opened <em>on</em>. The head
 * repository is named either by the optional {@code input.headRepositoryId}, or only by an {@code owner:} prefix on
 * {@code headRefName} (what {@code gh} sends). The owner prefix names an account, not a repository: GitHub pairs it
 * with one of that owner's repositories in the base repository's fork network, whatever it is named, even when the
 * owner holds several forks of it.
 *
 * <p>That pairing is GitHub's own, and {@code Ref.compare} performs it: the head is resolved by comparing the base
 * branch ({@code input.baseRefName}) against {@code headRefName} exactly as sent, so the SHA checked is the one GitHub
 * will open the PR from. When {@code headRepositoryId} is sent, it names the repository exactly, so the branch is read
 * from that repository's node instead. A {@code headRefName} with more than one {@code :} is refused: compare ignores a
 * repository segment, so it would check a different fork's branch than one that segment names. A head GitHub cannot
 * resolve resolves empty, which the caller refuses.
 */
@Slf4j
public class GitHubHeadShaResolver {

    private static final String COMPARE_QUERY =
            "query($owner: String!, $name: String!, $baseRef: String!, $headRef: String!) {"
                    + " repository(owner: $owner, name: $name) {"
                    + " ref(qualifiedName: $baseRef) {"
                    + " compare(headRef: $headRef) { headTarget { oid } } } } }";

    /** {@code baseRefName} is a branch name; a fully qualified one is accepted too. */
    private static final String BRANCH_PREFIX = "refs/heads/";

    private static final String NODE_REF_QUERY = "query($id: ID!, $qualifiedName: String!) {"
            + " node(id: $id) { ... on Repository {"
            + " owner { login } ref(qualifiedName: $qualifiedName) { target { oid } } } } }";

    /**
     * {@code mergePullRequest}'s own input carries no head-SHA field at all — {@code gh} sends only
     * {@code pullRequestId} and {@code mergeMethod} (verified live), unlike {@code createPullRequest}'s
     * {@code headRefName}. So provenance at merge time cannot read the mutation the same way; it asks the PR's node
     * directly for its current head, the same node ID the mutation itself targets.
     */
    private static final String PULL_REQUEST_HEAD_QUERY =
            "query($id: ID!) { node(id: $id) { ... on PullRequest { headRefOid } } }";

    private static final JsonMapper MAPPER = new JsonMapper();

    /** Matches the bound the other inline provider lookups use — see {@link GitHubNodeIdResolver}. */
    private static final Timeout RESOLVE_TIMEOUT = Timeout.ofSeconds(10);

    /**
     * Resolves {@code headRefName} to the tip SHA GitHub pairs it with against {@code baseRefName}, using
     * {@code callerToken} — the caller's own upstream credential, per the BYO-token model. Empty means the head could
     * not be resolved and must be treated as "no push record", never skipped.
     */
    public Optional<String> resolveHeadSha(
            GitHubProvider provider,
            OwnerRepo baseRepo,
            Optional<String> baseRefName,
            String headRefName,
            Optional<String> headRepositoryId,
            String callerToken) {
        if (baseRefName.isEmpty()) {
            log.warn("No baseRefName to resolve head ref '{}' against; refusing", headRefName);
            return Optional.empty();
        }
        if (headRefName.indexOf(':') != headRefName.lastIndexOf(':')) {
            log.warn(
                    "headRefName '{}' names a repository segment; only branch or owner:branch is accepted",
                    headRefName);
            return Optional.empty();
        }
        try {
            if (headRepositoryId.isPresent()) {
                return resolveByNodeId(provider, headRepositoryId.get(), headRefName, callerToken);
            }
            String baseBranch = baseRefName.get().startsWith(BRANCH_PREFIX)
                    ? baseRefName.get().substring(BRANCH_PREFIX.length())
                    : baseRefName.get();
            return compare(provider, baseRepo, BRANCH_PREFIX + baseBranch, headRefName, callerToken);
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve head ref '{}' for provider '{}': {}",
                    headRefName,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves a pull request's current head SHA directly from its node ID, using {@code callerToken}. Used for
     * {@code mergePullRequest}, whose input names no head ref or SHA of its own — see {@link #PULL_REQUEST_HEAD_QUERY}.
     */
    public Optional<String> resolvePullRequestHeadSha(
            GitHubProvider provider, String pullRequestNodeId, String callerToken) {
        try {
            JsonNode oid = graphql(provider, PULL_REQUEST_HEAD_QUERY, Map.of("id", pullRequestNodeId), callerToken)
                    .path("data")
                    .path("node")
                    .path("headRefOid");
            return oid.isString() ? Optional.of(oid.asString()) : Optional.empty();
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve pull request head SHA for node '{}' on provider '{}': {}",
                    pullRequestNodeId,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads {@code headRefName}'s branch from the repository {@code headRepositoryId} names. An {@code owner:} prefix
     * on {@code headRefName} must name that repository's owner; a disagreement leaves which repository GitHub opens the
     * PR from ambiguous, so it resolves empty.
     */
    private Optional<String> resolveByNodeId(
            GitHubProvider provider, String headRepositoryId, String headRefName, String callerToken) throws Exception {
        int colon = headRefName.indexOf(':');
        JsonNode response = graphql(
                provider,
                NODE_REF_QUERY,
                Map.of("id", headRepositoryId, "qualifiedName", BRANCH_PREFIX + headRefName.substring(colon + 1)),
                callerToken);
        JsonNode node = response.path("data").path("node");
        JsonNode login = node.path("owner").path("login");
        if (hasErrors(response) || !login.isString()) {
            log.warn("headRepositoryId '{}' does not name a repository; refusing", headRepositoryId);
            return Optional.empty();
        }
        if (colon >= 0 && !headRefName.substring(0, colon).equalsIgnoreCase(login.asString())) {
            log.warn(
                    "headRefName owner '{}' does not own headRepositoryId '{}' (owner '{}'); refusing",
                    headRefName.substring(0, colon),
                    headRepositoryId,
                    login.asString());
            return Optional.empty();
        }
        JsonNode oid = node.path("ref").path("target").path("oid");
        return oid.isString() ? Optional.of(oid.asString()) : Optional.empty();
    }

    private Optional<String> compare(
            GitHubProvider provider, OwnerRepo baseRepo, String baseRef, String headRef, String callerToken)
            throws Exception {
        JsonNode response = graphql(
                provider,
                COMPARE_QUERY,
                Map.of("owner", baseRepo.owner(), "name", baseRepo.name(), "baseRef", baseRef, "headRef", headRef),
                callerToken);
        JsonNode oid = response.path("data")
                .path("repository")
                .path("ref")
                .path("compare")
                .path("headTarget")
                .path("oid");
        if (hasErrors(response) || !oid.isString()) {
            log.warn(
                    "GitHub could not resolve head ref '{}' against {}/{} {} for provider '{}': {}",
                    headRef,
                    baseRepo.owner(),
                    baseRepo.name(),
                    baseRef,
                    provider.getProviderId(),
                    response.path("errors"));
            return Optional.empty();
        }
        return Optional.of(oid.asString());
    }

    private static boolean hasErrors(JsonNode response) {
        JsonNode errors = response.path("errors");
        return !errors.isMissingNode() && !errors.isNull() && !(errors.isArray() && errors.isEmpty());
    }

    /**
     * Posts one GraphQL query with the caller's token and returns the whole response, {@code data} and {@code errors}.
     */
    private static JsonNode graphql(
            GitHubProvider provider, String query, Map<String, Object> variables, String callerToken) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("query", query, "variables", variables));
        Request request = Request.post(provider.getGraphqlUrl()).addHeader("Authorization", "Bearer " + callerToken);
        ScmApiUserAgent.self(request);
        String response = request.bodyString(body, ContentType.APPLICATION_JSON)
                .connectTimeout(RESOLVE_TIMEOUT)
                .responseTimeout(RESOLVE_TIMEOUT)
                .execute(FogwallHttpExecutor.instance())
                .returnContent()
                .asString();
        return MAPPER.readTree(response);
    }
}
