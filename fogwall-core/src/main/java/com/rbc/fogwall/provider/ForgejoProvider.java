package com.rbc.fogwall.provider;

import java.net.URI;
import java.util.Optional;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
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
public class ForgejoProvider extends AbstractFogwallProvider implements TokenIdentityProvider {

    public static final String TYPE = "forgejo";

    // Codeberg is a FOSS friendly Forgejo provider and is the default that ships with the app
    // https://gitea.com is also compatible (but is not as FOSS friendly given that it is commercial backed)
    // Both are equivalent in terms of proxying and API compatibility
    public static final URI CODEBERG = URI.create("https://codeberg.org");
    public static final URI GITEA = URI.create("https://gitea.com");

    @Builder
    public ForgejoProvider(String name, URI uri, String pathSuffix) {
        super(name, TYPE, uri, pathSuffix);
    }

    public String getApiUrl() {
        return String.format("%s/api/v1", uri);
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
    public Optional<ScmUserInfo> fetchScmIdentity(String pushUsername, String token) {
        try {
            var response = Request.get(getApiUrl() + "/user")
                    .addHeader("Authorization", "token " + token)
                    .execute()
                    .returnContent()
                    .asString();
            var info = new JsonMapper().readValue(response, ForgejoUserInfo.class);
            return Optional.of(new ScmUserInfo(info.login(), Optional.ofNullable(info.email())));
        } catch (Exception e) {
            log.warn("Failed to fetch Forgejo identity (missing scope or invalid token?): {}", e.getMessage());
            return Optional.empty();
        }
    }
}

/** Jackson deserialization target for the Forgejo/Gitea {@code GET /api/v1/user} response. */
record ForgejoUserInfo(String login, Long id, String email) {}
