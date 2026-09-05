package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.GitLabProvider;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves a GitLab numeric project ID to the {@code owner/repo} it names — GitLab's counterpart to
 * {@link NodeIdResolver}, and the reason fogwall can authorize a fork merge request against the upstream it targets.
 *
 * <p>{@code glab mr create} puts the <b>source</b> project in the URL and carries the upstream only as a numeric
 * {@code target_project_id} in the request body (verified against a real fork MR — see
 * docs/internals/SCM_API_PROXY.md). Authorizing on the URL alone would therefore check the fork, which the contributor
 * owns and can always push to, rather than the upstream the merge request is actually opened on.
 *
 * <p>Backed by {@link GitLabProjectIdCache}, its own store rather than the GitHub node-ID cache: the two are different
 * identifiers from different APIs that merely share a resolution shape. Its TTL is a security parameter for the same
 * reason the node-ID cache's is — an ID outlives a rename or transfer while what it resolves to changes underneath it.
 */
@Slf4j
public class GitLabProjectIdResolver {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final GitLabProjectIdCache cache;

    public GitLabProjectIdResolver(GitLabProjectIdCache cache) {
        this.cache = cache;
    }

    /**
     * Resolves {@code projectId} to the project it names, presenting {@code authHeaderName}/{@code authHeaderValue} —
     * the caller's own credential, relayed in the header they used, never re-schemed. Cache first; only calls upstream
     * on a miss. Empty means "could not be resolved", which the caller must treat as a denial rather than falling back
     * to the URL, since an unresolvable target is precisely the case where the URL names the wrong repository.
     */
    public Optional<OwnerRepo> resolve(
            GitLabProvider provider, String projectId, String authHeaderName, String authHeaderValue) {
        Optional<OwnerRepo> cached = cache.lookup(provider.getProviderId(), projectId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<OwnerRepo> resolved = resolveUpstream(provider, projectId, authHeaderName, authHeaderValue);
        resolved.ifPresent(ownerRepo -> cache.store(provider.getProviderId(), projectId, ownerRepo));
        return resolved;
    }

    private Optional<OwnerRepo> resolveUpstream(
            GitLabProvider provider, String projectId, String authHeaderName, String authHeaderValue) {
        try {
            var request = Request.get(provider.getApiUrl() + "/projects/" + projectId);
            if (authHeaderName != null) {
                request.addHeader(authHeaderName, authHeaderValue);
            }
            String response = request.execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            return extractOwnerRepo(MAPPER.readTree(response));
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve GitLab project ID '{}' for provider '{}': {}",
                    projectId,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Splits {@code path_with_namespace} on its last separator. GitLab nests groups, so everything before that
     * separator is the owner — {@code group/subgroup/widgets} owns {@code widgets} under {@code group/subgroup}, which
     * is the same shape the URL-addressed path produces.
     */
    private static Optional<OwnerRepo> extractOwnerRepo(JsonNode project) {
        if (project == null || project.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode pathNode = project.get("path_with_namespace");
        if (pathNode == null || !pathNode.isString()) {
            return Optional.empty();
        }
        String path = pathNode.asString();
        int idx = path.lastIndexOf('/');
        if (idx <= 0 || idx == path.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new OwnerRepo(path.substring(0, idx), path.substring(idx + 1)));
    }
}
