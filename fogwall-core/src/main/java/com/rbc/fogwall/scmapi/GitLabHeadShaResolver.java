package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.GitLabProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves an {@code mr create} request's {@code source_branch} to the tip commit SHA it currently names, for
 * {@code providers.gitlab.proposals.require-validated-head} (see {@link HeadCommitValidator}).
 *
 * <p>Unlike GitHub and Forgejo, GitLab's fork is not named in the body at all — {@code mr create} always POSTs to the
 * <b>source</b> project's own URL (see the "Fork MRs address the source project" section of the SCM API proxy notes),
 * so the caller already has that project's {@code owner/repo} before this resolver is reached; it needs no fork parsing
 * of its own.
 */
@Slf4j
public class GitLabHeadShaResolver {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Timeout RESOLVE_TIMEOUT = Timeout.ofSeconds(10);

    /**
     * Resolves {@code branch} to its tip SHA in {@code sourceRepo}, presenting {@code authHeaderName}/
     * {@code authHeaderValue} — the caller's own upstream credential, per the BYO-token model. Empty means the branch
     * could not be resolved and must be treated as "no push record", never skipped.
     */
    public Optional<String> resolveHeadSha(
            GitLabProvider provider,
            OwnerRepo sourceRepo,
            String branch,
            String authHeaderName,
            String authHeaderValue) {
        String path = URLEncoder.encode(sourceRepo.owner() + "/" + sourceRepo.name(), StandardCharsets.UTF_8);
        String encodedBranch = URLEncoder.encode(branch, StandardCharsets.UTF_8);
        try {
            var request =
                    Request.get(provider.getApiUrl() + "/projects/" + path + "/repository/branches/" + encodedBranch);
            if (authHeaderName != null) {
                request.addHeader(authHeaderName, authHeaderValue);
            }
            ScmApiUserAgent.self(request);
            String response = request.connectTimeout(RESOLVE_TIMEOUT)
                    .responseTimeout(RESOLVE_TIMEOUT)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            return extractSha(MAPPER.readTree(response));
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve branch '{}' in {}/{} for provider '{}': {}",
                    branch,
                    sourceRepo.owner(),
                    sourceRepo.name(),
                    provider.getProviderId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> extractSha(JsonNode branchResponse) {
        JsonNode id = branchResponse.path("commit").path("id");
        return id.isString() ? Optional.of(id.asString()) : Optional.empty();
    }
}
