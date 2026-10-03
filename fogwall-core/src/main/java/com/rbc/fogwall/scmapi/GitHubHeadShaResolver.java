package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.GitHubProvider;
import java.util.HashMap;
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
 * {@code headRefName} (what {@code gh} sends). A fork may be renamed away from the base repository's name, so the owner
 * prefix alone does not name a repository; it is resolved the way GitHub itself pairs an owner with a base repository's
 * network, in this order:
 *
 * <ol>
 *   <li>{@code input.headRepositoryId}, when supplied.
 *   <li>The head owner's repository of the base repository's name, only when it is a fork whose parent is the base.
 *   <li>The base repository's own parent, when the head owner owns it — a PR from upstream into a fork.
 *   <li>The head owner's forks, most recently pushed first, bounded to {@link #MAX_FORK_PAGES} pages — the one whose
 *       parent is the base.
 * </ol>
 *
 * <p>The base repository's fork network is never listed: a popular upstream has thousands of forks, and this lookup
 * sits inline on the mutation. A head repository none of these finds resolves empty, which the caller refuses.
 */
@Slf4j
public class GitHubHeadShaResolver {

    /** Pages of the head owner's forks searched before giving up — each page is one inline GraphQL call. */
    static final int MAX_FORK_PAGES = 3;

    static final int FORK_PAGE_SIZE = 100;

    private static final String REF_QUERY = "query($owner: String!, $name: String!, $qualifiedName: String!) {"
            + " repository(owner: $owner, name: $name) {"
            + " ref(qualifiedName: $qualifiedName) { target { oid } } } }";

    private static final String NODE_REF_QUERY = "query($id: ID!, $qualifiedName: String!) {"
            + " node(id: $id) { ... on Repository {"
            + " owner { login } ref(qualifiedName: $qualifiedName) { target { oid } } } } }";

    private static final String CANDIDATE_QUERY =
            "query($baseOwner: String!, $baseName: String!, $headOwner: String!) {"
                    + " base: repository(owner: $baseOwner, name: $baseName) {"
                    + " parent { nameWithOwner owner { login } } }"
                    + " sameName: repository(owner: $headOwner, name: $baseName) {"
                    + " nameWithOwner parent { nameWithOwner } } }";

    private static final String OWNER_FORKS_QUERY = "query($owner: String!, $first: Int!, $after: String) {"
            + " repositoryOwner(login: $owner) {"
            + " repositories(isFork: true, first: $first, after: $after,"
            + " orderBy: {field: PUSHED_AT, direction: DESC}) {"
            + " nodes { nameWithOwner parent { nameWithOwner } }"
            + " pageInfo { hasNextPage endCursor } } } }";

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
     * Resolves {@code headRefName} to its tip SHA in the head repository, using {@code callerToken} — the caller's own
     * upstream credential, per the BYO-token model. Empty means the head repository or branch could not be resolved and
     * must be treated as "no push record", never skipped.
     */
    public Optional<String> resolveHeadSha(
            GitHubProvider provider,
            OwnerRepo baseRepo,
            String headRefName,
            Optional<String> headRepositoryId,
            String callerToken) {
        int colon = headRefName.indexOf(':');
        Optional<String> headOwner = colon < 0 ? Optional.empty() : Optional.of(headRefName.substring(0, colon));
        String qualifiedName = "refs/heads/" + headRefName.substring(colon + 1);
        try {
            if (headRepositoryId.isPresent()) {
                return resolveByNodeId(provider, headRepositoryId.get(), headOwner, qualifiedName, callerToken);
            }
            if (headOwner.isEmpty() || headOwner.get().equalsIgnoreCase(baseRepo.owner())) {
                return resolveRef(provider, baseRepo, qualifiedName, callerToken);
            }
            Optional<OwnerRepo> headRepo = resolveHeadRepository(provider, baseRepo, headOwner.get(), callerToken);
            if (headRepo.isEmpty()) {
                log.warn(
                        "No repository owned by '{}' is a fork of {}/{} for provider '{}'",
                        headOwner.get(),
                        baseRepo.owner(),
                        baseRepo.name(),
                        provider.getProviderId());
                return Optional.empty();
            }
            return resolveRef(provider, headRepo.get(), qualifiedName, callerToken);
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
     * An {@code owner:} prefix alongside {@code headRepositoryId} must name that repository's owner; a disagreement
     * leaves which repository GitHub opens the PR from ambiguous, so it resolves empty.
     */
    private Optional<String> resolveByNodeId(
            GitHubProvider provider,
            String headRepositoryId,
            Optional<String> headOwner,
            String qualifiedName,
            String callerToken)
            throws Exception {
        JsonNode node = graphql(
                        provider,
                        NODE_REF_QUERY,
                        Map.of("id", headRepositoryId, "qualifiedName", qualifiedName),
                        callerToken)
                .path("node");
        JsonNode login = node.path("owner").path("login");
        if (!login.isString()) {
            return Optional.empty();
        }
        if (headOwner.isPresent() && !headOwner.get().equalsIgnoreCase(login.asString())) {
            log.warn(
                    "headRefName owner '{}' does not own headRepositoryId '{}' (owner '{}'); refusing",
                    headOwner.get(),
                    headRepositoryId,
                    login.asString());
            return Optional.empty();
        }
        return oid(node);
    }

    private Optional<OwnerRepo> resolveHeadRepository(
            GitHubProvider provider, OwnerRepo baseRepo, String headOwner, String callerToken) throws Exception {
        String baseNameWithOwner = baseRepo.owner() + "/" + baseRepo.name();
        JsonNode candidates = graphql(
                provider,
                CANDIDATE_QUERY,
                Map.of("baseOwner", baseRepo.owner(), "baseName", baseRepo.name(), "headOwner", headOwner),
                callerToken);

        JsonNode sameName = candidates.path("sameName");
        if (isForkOf(sameName, baseNameWithOwner)) {
            return toOwnerRepo(sameName.path("nameWithOwner"));
        }
        JsonNode baseParent = candidates.path("base").path("parent");
        JsonNode parentOwner = baseParent.path("owner").path("login");
        if (parentOwner.isString() && parentOwner.asString().equalsIgnoreCase(headOwner)) {
            return toOwnerRepo(baseParent.path("nameWithOwner"));
        }
        return searchOwnerForks(provider, baseNameWithOwner, headOwner, callerToken);
    }

    private Optional<OwnerRepo> searchOwnerForks(
            GitHubProvider provider, String baseNameWithOwner, String headOwner, String callerToken) throws Exception {
        Optional<String> cursor = Optional.empty();
        for (int page = 0; page < MAX_FORK_PAGES; page++) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("owner", headOwner);
            variables.put("first", FORK_PAGE_SIZE);
            cursor.ifPresent(after -> variables.put("after", after));
            JsonNode repositories = graphql(provider, OWNER_FORKS_QUERY, variables, callerToken)
                    .path("repositoryOwner")
                    .path("repositories");
            for (JsonNode fork : repositories.path("nodes")) {
                if (isForkOf(fork, baseNameWithOwner)) {
                    return toOwnerRepo(fork.path("nameWithOwner"));
                }
            }
            JsonNode pageInfo = repositories.path("pageInfo");
            JsonNode endCursor = pageInfo.path("endCursor");
            if (!pageInfo.path("hasNextPage").asBoolean(false) || !endCursor.isString()) {
                return Optional.empty();
            }
            cursor = Optional.of(endCursor.asString());
        }
        log.warn(
                "Stopped searching '{}' for a fork of {} after {} pages of {}",
                headOwner,
                baseNameWithOwner,
                MAX_FORK_PAGES,
                FORK_PAGE_SIZE);
        return Optional.empty();
    }

    private Optional<String> resolveRef(
            GitHubProvider provider, OwnerRepo repo, String qualifiedName, String callerToken) throws Exception {
        return oid(graphql(
                        provider,
                        REF_QUERY,
                        Map.of("owner", repo.owner(), "name", repo.name(), "qualifiedName", qualifiedName),
                        callerToken)
                .path("repository"));
    }

    private static boolean isForkOf(JsonNode repository, String baseNameWithOwner) {
        JsonNode parent = repository.path("parent").path("nameWithOwner");
        return parent.isString() && parent.asString().equalsIgnoreCase(baseNameWithOwner);
    }

    private static Optional<OwnerRepo> toOwnerRepo(JsonNode nameWithOwner) {
        if (!nameWithOwner.isString()) {
            return Optional.empty();
        }
        String value = nameWithOwner.asString();
        int slash = value.indexOf('/');
        return slash <= 0 || slash == value.length() - 1
                ? Optional.empty()
                : Optional.of(new OwnerRepo(value.substring(0, slash), value.substring(slash + 1)));
    }

    private static Optional<String> oid(JsonNode repository) {
        JsonNode oid = repository.path("ref").path("target").path("oid");
        return oid.isString() ? Optional.of(oid.asString()) : Optional.empty();
    }

    /** Posts one GraphQL query with the caller's token and returns its {@code data} object. */
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
        return MAPPER.readTree(response).path("data");
    }
}
