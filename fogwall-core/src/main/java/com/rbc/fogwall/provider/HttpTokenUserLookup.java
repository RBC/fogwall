package com.rbc.fogwall.provider;

import java.util.Optional;

/**
 * Optional capability implemented by {@link FogwallProvider}s that can verify a pusher's SCM identity from an HTTP
 * Basic-auth token.
 *
 * <p>When a user pushes over HTTP, their git client supplies a Personal Access Token as the Basic-auth password.
 * Fogwall calls {@link #fetchUserFromHttp} to exchange that token for the provider's canonical SCM login, which is then
 * matched against {@code user_scm_identities} to find the corresponding proxy user.
 *
 * <p>This is a critical differentiator from the original finos/git-proxy: that project uses the HTTP Basic-auth
 * username directly as the pusher identity, which is trivially spoofable — any client can claim any username. Fogwall
 * instead validates identity by calling the provider's authenticated user API with the token, making the token (not the
 * username) the source of truth for who is pushing.
 *
 * <p>Each implementation is responsible for:
 *
 * <ul>
 *   <li>Constructing the correct API endpoint (e.g. {@code GET /user} for GitHub, {@code GET /api/v4/user} for GitLab)
 *   <li>Using the correct auth header format ({@code Authorization: token <pat>} vs {@code Bearer <token>})
 *   <li>Extracting the normalised {@link ScmUserInfo} from the response
 *   <li>Returning {@code Optional.empty()} on any HTTP or parsing error, including missing token scopes
 * </ul>
 *
 * <p>The {@code pushUsername} is the HTTP Basic-auth username from the git client. Most providers ignore it (they
 * authenticate solely by token). Bitbucket is the exception — it requires the email address as the Basic-auth username
 * for its API.
 *
 * <p>The SSH equivalent of this capability is {@link SshKeyFingerprintLookup}: same question ("which SCM user is
 * this?"), answered via public key fingerprint instead of token.
 */
public interface HttpTokenUserLookup {

    /**
     * Exchange HTTP Basic-auth credentials for the SCM identity of the authenticated user.
     *
     * @param pushUsername the HTTP Basic-auth username supplied by the git client (may be ignored by most providers)
     * @param token the HTTP Basic-auth password / personal access token supplied by the git client
     * @return the canonical SCM identity, or empty if the token is invalid, expired, or lacks the required scope
     */
    Optional<ScmUserInfo> fetchUserFromHttp(String pushUsername, String token);
}
