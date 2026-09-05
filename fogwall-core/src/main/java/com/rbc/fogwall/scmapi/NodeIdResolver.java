package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.GitHubProvider;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves an opaque GraphQL node ID to the {@code owner/repo} it belongs to, so the SCM API proxy's authorization step
 * has a concrete permission-check target — mutations reference their subject only by node ID, never by owner/repo
 * (docs/internals/SCM_API_PROXY.md §3c).
 *
 * <p>GitHub-specific: the {@code node(id:)} query below is GitHub's own GraphQL schema shape, so serving another
 * provider means its own resolver rather than a generalisation of this one — {@link GitLabProjectIdResolver} is that
 * counterpart, resolving a numeric project ID over REST.
 */
@Slf4j
public class NodeIdResolver {

    private static final String NODE_QUERY = "query($id: ID!) { node(id: $id) {"
            + " ... on Repository { name owner { login } }"
            + " ... on Issue { repository { name owner { login } } }"
            + " ... on PullRequest { repository { name owner { login } } } } }";

    private static final JsonMapper MAPPER = new JsonMapper();

    private final NodeIdCache cache;

    public NodeIdResolver(NodeIdCache cache) {
        this.cache = cache;
    }

    /**
     * Resolves {@code ref} to the repository it belongs to, using {@code callerToken} — the CLI caller's own upstream
     * credential; fogwall never uses its own credential for this lookup, per the BYO-token model. Cache first; only
     * calls upstream on a miss.
     */
    public Optional<OwnerRepo> resolve(GitHubProvider provider, MutationNodeIdRef ref, String callerToken) {
        Optional<OwnerRepo> cached = cache.lookup(provider.getProviderId(), ref.nodeId());
        if (cached.isPresent()) {
            return cached;
        }
        Optional<OwnerRepo> resolved = resolveUpstream(provider, ref.nodeId(), callerToken);
        resolved.ifPresent(ownerRepo -> cache.store(provider.getProviderId(), ref.nodeId(), ownerRepo));
        return resolved;
    }

    /**
     * Seeds the cache from a preceding read query's own {@code (owner, name) -> id} response — see §3c's cache-seed
     * path, which collapses the cold-cache resolution call to near-zero in the common flow.
     */
    public void seed(GitHubProvider provider, String nodeId, OwnerRepo ownerRepo) {
        cache.store(provider.getProviderId(), nodeId, ownerRepo);
    }

    private Optional<OwnerRepo> resolveUpstream(GitHubProvider provider, String nodeId, String callerToken) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("query", NODE_QUERY, "variables", Map.of("id", nodeId)));
            String response = Request.post(provider.getGraphqlUrl())
                    .addHeader("Authorization", "Bearer " + callerToken)
                    .bodyString(body, ContentType.APPLICATION_JSON)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            return extractOwnerRepo(MAPPER.readTree(response).path("data").path("node"));
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve node ID '{}' for provider '{}': {}",
                    nodeId,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    /** {@code node} is either a Repository directly, or an Issue/PullRequest wrapping one under "repository". */
    private static Optional<OwnerRepo> extractOwnerRepo(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        JsonNode repo = node.has("repository") ? node.get("repository") : node;
        JsonNode ownerNode = repo.get("owner");
        JsonNode nameNode = repo.get("name");
        if (ownerNode == null || nameNode == null || !nameNode.isString()) {
            return Optional.empty();
        }
        JsonNode loginNode = ownerNode.get("login");
        if (loginNode == null || !loginNode.isString()) {
            return Optional.empty();
        }
        return Optional.of(new OwnerRepo(loginNode.asString(), nameNode.asString()));
    }
}
