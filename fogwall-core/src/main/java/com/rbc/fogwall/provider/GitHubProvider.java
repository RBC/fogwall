package com.rbc.fogwall.provider;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.ssh.SshKeyUtils;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class GitHubProvider extends AbstractFogwallProvider implements HttpTokenUserLookup, SshKeyFingerprintLookup {

    public static final String NAME = "github";
    public static final URI DEFAULT_URI = URI.create("https://github.com");

    @Builder
    public GitHubProvider(String name, URI uri, String pathSuffix) {
        super(name != null ? name : NAME, NAME, uri != null ? uri : DEFAULT_URI, pathSuffix);
    }

    public GitHubProvider(String pathSuffix) {
        this(NAME, DEFAULT_URI, pathSuffix);
    }

    public String getApiUrl() {
        if (uri.equals(DEFAULT_URI)) {
            return "https://api.github.com";
        }
        if (isGhe()) {
            return String.format("https://api.%s", uri.getHost());
        }
        // fallback to self-hosted GHES
        return String.format("%s/api/v3", uri);
    }

    public String getGraphqlUrl() {
        if (uri.equals(DEFAULT_URI)) {
            return "https://api.github.com/graphql";
        }
        if (isGhe()) {
            return String.format("https://api.%s/graphql", uri.getHost());
        }
        // fallback to self-hosted GHES
        return String.format("%s/api/graphql", uri);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /user} with {@code Authorization: token <pat>}. GitHub emails are often empty because users
     * default to private email visibility — the {@link ScmUserInfo#email()} field will be empty in that case.
     *
     * <p>Required token scope: {@code read:user} (or {@code user} for classic PATs).
     */
    @Override
    public Optional<ScmUserInfo> fetchUserFromHttp(String pushUsername, String token) {
        try {
            var response = Request.get(getApiUrl() + "/user")
                    .addHeader("Authorization", "token " + token)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            var info = new JsonMapper().readValue(response, GitHubUserInfo.class);
            return Optional.of(new ScmUserInfo(info.login(), info.email()));
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub identity (missing scope or invalid token?): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls the unauthenticated {@code GET /users/{login}/keys} endpoint. GitHub does not include a fingerprint
     * field in the response, so fingerprints are computed from the raw public key string using
     * {@link SshKeyUtils#fingerprint}.
     */
    @Override
    public Set<String> fetchSshFingerprints(String login) {
        try {
            var response = Request.get(getApiUrl() + "/users/" + login + "/keys")
                    .connectTimeout(Timeout.ofSeconds(10))
                    .responseTimeout(Timeout.ofSeconds(10))
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            var keys = new JsonMapper().readValue(response, GitHubPublicKey[].class);
            return Arrays.stream(keys)
                    .map(k -> {
                        try {
                            return SshKeyUtils.fingerprint(k.key());
                        } catch (Exception e) {
                            log.warn("Could not fingerprint GitHub key for '{}': {}", login, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to fetch SSH keys for GitHub user '{}': {}", login, e.getMessage());
            return Set.of();
        }
    }

    @Override
    public Optional<String> buildRepoUrl(String owner, String repo) {
        return webBaseUrl().map(base -> base + "/" + owner + "/" + repo);
    }

    @Override
    public Optional<String> buildCommitUrl(String owner, String repo, String sha) {
        return buildRepoUrl(owner, repo).map(repoUrl -> repoUrl + "/commit/" + sha);
    }

    /**
     * Determines if the provider is a GitHub Enterprise Cloud with data residency. These instances have a custom domain
     * (e.g. mycompany.ghe.com) and use a different API path from GHEC or self-hosted GHES.
     *
     * @see <a
     *     href="https://docs.github.com/en/enterprise-cloud@latest/admin/data-residency/network-details-for-ghecom">GHE.com
     *     network documentation</a>
     */
    private boolean isGhe() {
        return uri.getHost().endsWith(".ghe.com");
    }
}

/** Jackson deserialization target for the GitHub {@code GET /users/{login}/keys} response. */
record GitHubPublicKey(long id, String key) {}

/** Jackson deserialization target for the GitHub {@code GET /user} response. */
record GitHubUserInfo(
        String login,
        Long id,
        // A user has to configure their profile explicitly to have a publicly visible email
        // for the value to be returned by the API. By most cases, it is null (Optional.empty()) due
        // to the default visibility of email being private.
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<String> email) {}
