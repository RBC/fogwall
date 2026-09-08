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
 * Resolves a {@code createPullRequest} mutation's {@code input.headRefName} to the tip commit SHA it currently names,
 * for {@code providers.github.proposals.require-validated-head} (see {@link HeadCommitValidator}).
 *
 * <p>{@code input.repositoryId} always names the base repository — the one a fork PR is opened <em>on</em>.
 * {@code headRefName} carries the fork only as an {@code owner:branch} prefix, with no separate repository name; GitHub
 * assumes the fork shares the base repository's name, which is what a normal fork does and the one thing
 * {@code headRefName}'s shape can express. {@code gh} does not send the schema's separate {@code headRepositoryId}, so
 * this is the only signal available (see the "Fork PRs address the upstream" section of the SCM API proxy notes).
 */
@Slf4j
public class GitHubHeadShaResolver {

    private static final String REF_QUERY = "query($owner: String!, $name: String!, $qualifiedName: String!) {"
            + " repository(owner: $owner, name: $name) {"
            + " ref(qualifiedName: $qualifiedName) { target { oid } } } }";

    private static final JsonMapper MAPPER = new JsonMapper();

    /** Matches the bound the other inline provider lookups use — see {@link GitHubNodeIdResolver}. */
    private static final Timeout RESOLVE_TIMEOUT = Timeout.ofSeconds(10);

    /**
     * Resolves {@code headRefName} to its tip SHA, using {@code callerToken} — the caller's own upstream credential,
     * per the BYO-token model. Empty means the branch could not be resolved (deleted, renamed, or the fork/branch does
     * not exist under the assumed name) and must be treated as "no push record", never skipped.
     */
    public Optional<String> resolveHeadSha(
            GitHubProvider provider, OwnerRepo baseRepo, String headRefName, String callerToken) {
        int colon = headRefName.indexOf(':');
        String owner = colon < 0 ? baseRepo.owner() : headRefName.substring(0, colon);
        String branch = colon < 0 ? headRefName : headRefName.substring(colon + 1);
        String qualifiedName = "refs/heads/" + branch;
        try {
            String body = MAPPER.writeValueAsString(Map.of(
                    "query",
                    REF_QUERY,
                    "variables",
                    Map.of("owner", owner, "name", baseRepo.name(), "qualifiedName", qualifiedName)));
            Request request =
                    Request.post(provider.getGraphqlUrl()).addHeader("Authorization", "Bearer " + callerToken);
            ScmApiUserAgent.self(request);
            String response = request.bodyString(body, ContentType.APPLICATION_JSON)
                    .connectTimeout(RESOLVE_TIMEOUT)
                    .responseTimeout(RESOLVE_TIMEOUT)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            return extractSha(MAPPER.readTree(response));
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve head ref '{}' for provider '{}': {}",
                    headRefName,
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> extractSha(JsonNode response) {
        JsonNode oid = response.path("data")
                .path("repository")
                .path("ref")
                .path("target")
                .path("oid");
        return oid.isString() ? Optional.of(oid.asString()) : Optional.empty();
    }
}
