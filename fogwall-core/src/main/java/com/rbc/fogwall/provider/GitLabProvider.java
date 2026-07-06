package com.rbc.fogwall.provider;

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
public class GitLabProvider extends AbstractFogwallProvider implements HttpTokenUserLookup, SshKeyFingerprintLookup {

    public static final URI DEFAULT_URI = URI.create("https://gitlab.com");
    public static final String NAME = "gitlab";

    @Builder
    public GitLabProvider(String name, URI uri, String pathSuffix, URI apiUri, String apiToken) {
        super(name != null ? name : NAME, NAME, uri != null ? uri : DEFAULT_URI, pathSuffix);
        this.apiUri = apiUri;
        this.apiToken = apiToken;
    }

    public GitLabProvider(String pathSuffix) {
        this(NAME, DEFAULT_URI, pathSuffix, null, null);
    }

    public String getApiUrl() {
        if (apiUri != null) return apiUri + "/api/v4";
        if ("ssh".equals(uri.getScheme())) return "https://" + uri.getHost() + "/api/v4";
        return uri + "/api/v4";
    }

    public String getGraphqlUrl() {
        return String.format("%s/api/graphql", uri);
    }

    public String getOAuthUrl() {
        return String.format("%s/oauth", uri);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/v4/user} with {@code Authorization: Bearer <token>}. GitLab returns the primary email
     * address even when the user has set it to not show on their profile.
     *
     * <p>Required token scope: {@code read_user} or {@code api}.
     */
    @Override
    public Optional<ScmUserInfo> fetchUserFromHttp(String pushUsername, String token) {
        try {
            var response = Request.get(getApiUrl() + "/user")
                    .addHeader("Authorization", "Bearer " + token)
                    .execute()
                    .returnContent()
                    .asString();
            var info = new JsonMapper().readValue(response, GitLabUserInfo.class);
            return Optional.of(new ScmUserInfo(info.username(), Optional.ofNullable(info.email())));
        } catch (Exception e) {
            log.warn("Failed to fetch GitLab identity (missing scope or invalid token?): {}", e.getMessage());
            return Optional.empty();
        }
    }
    /**
     * {@inheritDoc}
     *
     * <p>GitLab's key endpoint requires a user ID rather than a username. This is a two-step lookup:
     *
     * <ol>
     *   <li>{@code GET /api/v4/users?username={login}} — resolves the numeric user ID
     *   <li>{@code GET /api/v4/users/{id}/keys} — fetches the user's SSH public keys
     * </ol>
     *
     * <p>Both endpoints are publicly accessible for accounts with public profile visibility. Private GitLab instances
     * or accounts with restricted visibility may require a PAT — configure one via the provider settings if needed.
     */
    @Override
    public Set<String> fetchSshFingerprints(String login) {
        try {
            // Step 1: resolve user ID from username
            var userReq = Request.get(getApiUrl() + "/users?username=" + login)
                    .connectTimeout(Timeout.ofSeconds(10))
                    .responseTimeout(Timeout.ofSeconds(10));
            if (apiToken != null && !apiToken.isBlank()) {
                userReq = userReq.addHeader("Authorization", "Bearer " + apiToken);
            }
            var userResponse = userReq.execute().returnContent().asString();
            var users = new JsonMapper().readValue(userResponse, GitLabUserSearchResult[].class);
            if (users.length == 0) {
                log.warn("GitLab user '{}' not found", login);
                return Set.of();
            }
            long userId = users[0].id();

            // Step 2: fetch SSH keys for that user ID
            var keysReq = Request.get(getApiUrl() + "/users/" + userId + "/keys")
                    .connectTimeout(Timeout.ofSeconds(10))
                    .responseTimeout(Timeout.ofSeconds(10));
            if (apiToken != null && !apiToken.isBlank()) {
                keysReq = keysReq.addHeader("Authorization", "Bearer " + apiToken);
            }
            var keysResponse = keysReq.execute().returnContent().asString();
            var keys = new JsonMapper().readValue(keysResponse, GitLabPublicKey[].class);
            return Arrays.stream(keys)
                    .map(k -> {
                        try {
                            return SshKeyUtils.fingerprint(k.key());
                        } catch (Exception e) {
                            log.warn("Could not fingerprint GitLab key for '{}': {}", login, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Failed to fetch SSH keys for GitLab user '{}': {}", login, e.getMessage());
            return Set.of();
        }
    }
}

/** Jackson deserialization target for the GitLab {@code GET /api/v4/users?username=} response. */
record GitLabUserSearchResult(long id, String username) {}

/** Jackson deserialization target for the GitLab {@code GET /api/v4/users/{id}/keys} response. */
record GitLabPublicKey(long id, String title, String key) {}

/** Jackson deserialization target for the GitLab {@code GET /api/v4/user} response. */
record GitLabUserInfo(
        String username,
        Long id,
        // GitLab's API seems to return the default email address even if profile settings are set to "Do not show on
        // profile". This needs to be verified with more testing. This was tested with one account and the email
        // returned in the response was the primary email (not any secondary or committer emails).
        String email) {}
