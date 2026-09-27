package com.rbc.fogwall.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rbc.fogwall.crypto.TokenCipher;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.provider.ScmOAuthProvider;
import com.rbc.fogwall.user.ScmOAuthToken;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hands out the access token of a user's linked SCM OAuth token, refreshing it first when it has expired.
 *
 * <p>Providers differ in whether their access tokens expire: a classic GitHub OAuth App token does not, while GitLab,
 * Forgejo/Gitea and GitHub App user tokens do, some within hours. A caller that acts as the user upstream asks this
 * service for the access token at the moment it acts, never earlier, so a token read before a long wait is not the one
 * sent.
 *
 * <p>A refresh rotates the refresh token on most providers, which makes the old one unusable. Refreshes of the same
 * linked token are therefore serialised within the process, and a refresh the provider refuses re-reads the stored
 * token before giving up, in case another instance sharing the database refreshed it first.
 */
@Slf4j
public class ScmOAuthTokenService {

    /** Refresh this long before the recorded expiry, so a token is not handed out moments before it lapses. */
    static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private static final JsonMapper JSON = new JsonMapper();

    /** The outcome of asking for a linked token's access token. */
    public sealed interface Access permits Access.Usable, Access.Unusable {

        /**
         * A current access token, in plaintext, with the scopes the provider reported when the account was linked; null
         * when it reported none.
         */
        record Usable(String accessToken, String scopes) implements Access {}

        /** No usable token, and why. */
        record Unusable(Reason reason) implements Access {}
    }

    /** Why no usable access token could be produced. */
    public enum Reason {
        /** The user has not linked an account on this provider. */
        NOT_LINKED,
        /** The linked token has expired and could not be renewed; the user has to link the account again. */
        EXPIRED,
        /**
         * The linked token has expired and renewing it failed for a reason that may pass, such as the provider being
         * down.
         */
        REFRESH_FAILED,
        /** The token encryption key is not available, so no stored token can be read. */
        KEY_UNAVAILABLE
    }

    /** The OAuth application fogwall is registered as on one provider instance. */
    public record OAuthClient(String clientId, Path clientSecretPath) {}

    private final ScmOAuthTokenStore store;
    private final TokenCipherProvider cipherProvider;
    private final ProviderRegistry providers;
    private final Map<String, OAuthClient> clients;
    private final String serviceUrl;
    private final Clock clock;
    private final Map<String, Object> refreshLocks = new ConcurrentHashMap<>();

    /**
     * @param clients the OAuth application registered for each provider instance, keyed by provider name
     * @param serviceUrl fogwall's external base URL, from which the registered redirect URI is derived
     */
    public ScmOAuthTokenService(
            ScmOAuthTokenStore store,
            TokenCipherProvider cipherProvider,
            ProviderRegistry providers,
            Map<String, OAuthClient> clients,
            String serviceUrl) {
        this(store, cipherProvider, providers, clients, serviceUrl, Clock.systemUTC());
    }

    ScmOAuthTokenService(
            ScmOAuthTokenStore store,
            TokenCipherProvider cipherProvider,
            ProviderRegistry providers,
            Map<String, OAuthClient> clients,
            String serviceUrl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store is required: it holds the tokens this service hands out");
        this.cipherProvider = Objects.requireNonNull(cipherProvider, "cipherProvider is required");
        this.providers = Objects.requireNonNull(providers, "providers is required");
        this.clients = Map.copyOf(clients);
        this.serviceUrl = serviceUrl;
        this.clock = clock;
    }

    /** Returns the current access token {@code username} linked on {@code provider}, refreshing it if expired. */
    public Access access(String username, String provider) {
        Optional<TokenCipher> cipher = cipherProvider.cipher();
        if (cipher.isEmpty()) {
            return new Access.Unusable(Reason.KEY_UNAVAILABLE);
        }
        Optional<ScmOAuthToken> token = store.findToken(username, provider);
        if (token.isEmpty()) {
            return new Access.Unusable(Reason.NOT_LINKED);
        }
        if (isCurrent(token.get())) {
            return usable(cipher.get(), token.get());
        }
        synchronized (refreshLocks.computeIfAbsent(username + "\n" + provider, k -> new Object())) {
            // Another thread may have refreshed while this one waited; its rotation made our refresh token stale.
            token = store.findToken(username, provider);
            if (token.isEmpty()) {
                return new Access.Unusable(Reason.NOT_LINKED);
            }
            if (isCurrent(token.get())) {
                return usable(cipher.get(), token.get());
            }
            return refresh(username, provider, cipher.get(), token.get());
        }
    }

    /** Returns the providers {@code username} has linked a token on, whether or not it is still current. */
    public List<String> linkedProviders(String username) {
        return store.findLinkedProviders(username);
    }

    private Access refresh(String username, String provider, TokenCipher cipher, ScmOAuthToken token) {
        if (token.encryptedRefreshToken() == null) {
            log.info("OAuth token for user '{}' / provider '{}' expired and has no refresh token", username, provider);
            return new Access.Unusable(Reason.EXPIRED);
        }
        Optional<ScmOAuthProvider> oauthProvider = providers
                .getProvider(provider)
                .filter(ScmOAuthProvider.class::isInstance)
                .map(ScmOAuthProvider.class::cast);
        OAuthClient client = clients.get(provider);
        if (oauthProvider.isEmpty() || client == null) {
            log.warn(
                    "OAuth token for user '{}' / provider '{}' expired and cannot be refreshed: the provider has no"
                            + " OAuth application configured",
                    username,
                    provider);
            return new Access.Unusable(Reason.EXPIRED);
        }

        String refreshToken = new String(cipher.decrypt(token.encryptedRefreshToken()), StandardCharsets.UTF_8);
        RefreshOutcome outcome =
                exchange(oauthProvider.get().getOAuthTokenUrl(), client, provider, refreshToken, username);
        return switch (outcome) {
            case RefreshOutcome.Refreshed(TokenResponse tokens) -> {
                byte[] encryptedRefreshToken = tokens.refreshToken() != null
                        ? cipher.encrypt(tokens.refreshToken().getBytes(StandardCharsets.UTF_8))
                        : token.encryptedRefreshToken();
                Instant expiresAt = tokens.expiresInSeconds() != null
                        ? clock.instant().plusSeconds(tokens.expiresInSeconds())
                        : null;
                store.replaceTokens(
                        username,
                        provider,
                        cipher.encrypt(tokens.accessToken().getBytes(StandardCharsets.UTF_8)),
                        encryptedRefreshToken,
                        expiresAt);
                log.info("Refreshed OAuth token for user '{}' / provider '{}'", username, provider);
                yield new Access.Usable(tokens.accessToken(), token.scopes());
            }
            case RefreshOutcome.Refused() -> {
                // An instance sharing this database may have refreshed first, rotating the token we sent.
                Optional<ScmOAuthToken> reread = store.findToken(username, provider);
                if (reread.isPresent() && isCurrent(reread.get())) {
                    yield usable(cipher, reread.get());
                }
                yield new Access.Unusable(Reason.EXPIRED);
            }
            case RefreshOutcome.Failed() -> new Access.Unusable(Reason.REFRESH_FAILED);
        };
    }

    private sealed interface RefreshOutcome {
        record Refreshed(TokenResponse tokens) implements RefreshOutcome {}

        /** The provider refused the refresh token: revoked, expired, or already rotated. */
        record Refused() implements RefreshOutcome {}

        /** The exchange did not complete; the linked token may still be renewable. */
        record Failed() implements RefreshOutcome {}
    }

    private RefreshOutcome exchange(
            String tokenUrl, OAuthClient client, String provider, String refreshToken, String username) {
        String clientSecret;
        try {
            clientSecret = Files.readString(client.clientSecretPath()).strip();
        } catch (IOException e) {
            log.error("Cannot read the OAuth client secret for provider '{}': {}", provider, e.getMessage());
            return new RefreshOutcome.Failed();
        }
        var form = Form.form()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", client.clientId())
                .add("client_secret", clientSecret);
        // GitLab checks the redirect URI on a refresh against the one registered; the others ignore it.
        if (serviceUrl != null && !serviceUrl.isBlank()) {
            form.add("redirect_uri", serviceUrl + "/api/scm-oauth/" + provider + "/callback");
        }
        try {
            return Request.post(tokenUrl)
                    .addHeader("Accept", "application/json")
                    .bodyForm(form.build())
                    .execute(FogwallHttpExecutor.instance())
                    .handleResponse(response -> {
                        String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
                        int status = response.getCode();
                        if (status >= 500) {
                            log.warn(
                                    "OAuth refresh for user '{}' / provider '{}' failed with HTTP {}",
                                    username,
                                    provider,
                                    status);
                            return new RefreshOutcome.Failed();
                        }
                        Optional<TokenResponse> parsed = parse(body);
                        // GitHub reports a refused refresh as HTTP 200 with an error field.
                        Optional<TokenResponse> issued =
                                status < 400 ? parsed.filter(TokenResponse::issued) : Optional.empty();
                        if (issued.isEmpty()) {
                            log.info(
                                    "OAuth refresh for user '{}' / provider '{}' refused (HTTP {}, error '{}')",
                                    username,
                                    provider,
                                    status,
                                    parsed.map(TokenResponse::error).orElse(null));
                            return new RefreshOutcome.Refused();
                        }
                        return new RefreshOutcome.Refreshed(issued.get());
                    });
        } catch (IOException e) {
            log.warn("OAuth refresh for user '{}' / provider '{}' failed: {}", username, provider, e.getMessage());
            return new RefreshOutcome.Failed();
        }
    }

    /** Empty when the body is not a JSON token response, such as an HTML error page from a proxy. */
    private static Optional<TokenResponse> parse(String body) {
        try {
            return Optional.ofNullable(JSON.readValue(body, TokenResponse.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private boolean isCurrent(ScmOAuthToken token) {
        return token.expiresAt() == null || clock.instant().plus(EXPIRY_SKEW).isBefore(token.expiresAt());
    }

    private static Access usable(TokenCipher cipher, ScmOAuthToken token) {
        return new Access.Usable(
                new String(cipher.decrypt(token.encryptedAccessToken()), StandardCharsets.UTF_8), token.scopes());
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresInSeconds,
            @JsonProperty("error") String error) {

        /** Whether the provider issued new tokens, rather than answering with an error. */
        boolean issued() {
            return error == null && accessToken != null;
        }
    }
}
