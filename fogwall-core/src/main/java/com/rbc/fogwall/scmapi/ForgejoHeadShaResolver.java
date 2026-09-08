package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.ForgejoProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves a {@code pulls.create} request's {@code head} field to the tip commit SHA it currently names, for
 * {@code providers.<name>.proposals.require-validated-head} (see {@link HeadCommitValidator}).
 *
 * <p>{@code head} carries the fork as an {@code owner:branch} prefix, the same shape GitHub uses; the base repository
 * named in the URL path is otherwise assumed (see the "Fork PRs address the upstream" section of the SCM API proxy
 * notes).
 */
@Slf4j
public class ForgejoHeadShaResolver {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Timeout RESOLVE_TIMEOUT = Timeout.ofSeconds(10);

    /**
     * Resolves {@code head} to its tip SHA, using {@code callerToken} — the caller's own upstream credential, per the
     * BYO-token model. Empty means the branch could not be resolved and must be treated as "no push record", never
     * skipped.
     */
    public Optional<String> resolveHeadSha(
            ForgejoProvider provider, OwnerRepo baseRepo, String head, String callerToken) {
        int colon = head.indexOf(':');
        String owner = colon < 0 ? baseRepo.owner() : head.substring(0, colon);
        String branch = colon < 0 ? head : head.substring(colon + 1);
        String encodedBranch = URLEncoder.encode(branch, StandardCharsets.UTF_8);
        try {
            var request = Request.get(
                    provider.getApiUrl() + "/repos/" + owner + "/" + baseRepo.name() + "/branches/" + encodedBranch);
            if (callerToken != null) {
                request.addHeader("Authorization", "token " + callerToken);
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
                    "Failed to resolve head '{}' for provider '{}': {}",
                    head,
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
