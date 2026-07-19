package com.rbc.fogwall.provider;

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

/**
 * Provider for Forgejo and Gitea instances.
 *
 * <p>Forgejo exposes a Gitea-compatible REST API ({@code GET /api/v1/user}). Identity resolution returns both the
 * {@code login} and {@code email} fields.
 *
 * <p>Built-in reserved names: {@code codeberg} (defaults to {@code https://codeberg.org}) and {@code gitea} (defaults
 * to {@code https://gitea.com}). Custom-named providers targeting a self-hosted instance should set {@code type:
 * forgejo} (or {@code codeberg}/{@code gitea}) with an explicit {@code uri}.
 */
@Slf4j
public class ForgejoProvider extends AbstractFogwallProvider implements HttpTokenUserLookup, SshKeyFingerprintLookup {

    public static final String TYPE = "forgejo";

    // Codeberg is a FOSS friendly Forgejo provider and is the default that ships with the app
    // https://gitea.com is also compatible (but is not as FOSS friendly given that it is commercial backed)
    // Both are equivalent in terms of proxying and API compatibility
    public static final URI CODEBERG = URI.create("https://codeberg.org");
    public static final URI GITEA = URI.create("https://gitea.com");

    @Builder
    public ForgejoProvider(String name, URI uri, String pathSuffix, URI apiUri, String apiToken) {
        super(name, TYPE, uri, pathSuffix);
        this.apiUri = apiUri;
        this.apiToken = apiToken;
    }

    /**
     * Returns the base URL for Forgejo REST API calls. When an explicit {@code apiUri} is configured (for non-standard
     * port deployments), that takes precedence. Otherwise, for SSH URIs the HTTP API is derived by replacing the scheme
     * with {@code https} and using the hostname alone — correct for standard public deployments where SSH and HTTPS
     * share the same host. For HTTP/HTTPS URIs the transport URI is used directly.
     */
    public String getApiUrl() {
        if (apiUri != null) return apiUri + "/api/v1";
        if ("ssh".equals(uri.getScheme())) return "https://" + uri.getHost() + "/api/v1";
        return uri + "/api/v1";
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
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/user} with {@code Authorization: token <pat>}. Forgejo returns both {@code login} and
     * {@code email}, so both fields are populated.
     *
     * <p>Required token scope: {@code read:user}.
     */
    @Override
    public Optional<ScmUserInfo> fetchUserFromHttp(String pushUsername, String token) {
        try {
            var response = Request.get(getApiUrl() + "/user")
                    .addHeader("Authorization", "token " + token)
                    .execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            var info = new JsonMapper().readValue(response, ForgejoUserInfo.class);
            return Optional.of(new ScmUserInfo(info.login(), Optional.ofNullable(info.email())));
        } catch (Exception e) {
            log.warn("Failed to fetch Forgejo identity (missing scope or invalid token?): {}", e.getMessage());
            return Optional.empty();
        }
    }
    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v1/users/{login}/keys}. Forgejo's response includes a {@code fingerprint} field, but
     * fingerprints are computed from the raw {@code key} string for format consistency with other providers.
     */
    @Override
    public Set<String> fetchSshFingerprints(String login) {
        try {
            var request = Request.get(getApiUrl() + "/users/" + login + "/keys")
                    .connectTimeout(Timeout.ofSeconds(10))
                    .responseTimeout(Timeout.ofSeconds(10));
            if (apiToken != null && !apiToken.isBlank()) {
                request = request.addHeader("Authorization", "token " + apiToken);
            }
            var response = request.execute(FogwallHttpExecutor.instance())
                    .returnContent()
                    .asString();
            var keys = new JsonMapper().readValue(response, ForgejoPublicKey[].class);
            return Arrays.stream(keys)
                    .map(k -> {
                        try {
                            return SshKeyUtils.fingerprint(k.key());
                        } catch (Exception e) {
                            log.warn("Could not fingerprint Forgejo key for '{}': {}", login, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to fetch SSH keys for Forgejo user '{}': {}", login, e.getMessage());
            return Set.of();
        }
    }
}

/** Jackson deserialization target for the Forgejo/Gitea {@code GET /api/v1/users/{login}/keys} response. */
record ForgejoPublicKey(long id, String key, String fingerprint) {}

/** Jackson deserialization target for the Forgejo/Gitea {@code GET /api/v1/user} response. */
record ForgejoUserInfo(String login, Long id, String email) {}
