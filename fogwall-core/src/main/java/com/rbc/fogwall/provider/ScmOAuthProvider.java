package com.rbc.fogwall.provider;

import java.util.Arrays;
import java.util.Set;

/**
 * Optional capability implemented by {@link FogwallProvider}s that SCM account linking supports: the provider issues
 * OAuth tokens for an authorization code and renews them for a refresh token.
 *
 * <p>Both endpoints live on the provider's web host, which is not always the host its REST API is served from.
 */
public interface ScmOAuthProvider {

    /** The authorization endpoint the user is redirected to when linking an account. */
    String getOAuthAuthorizeUrl();

    /** The token endpoint, used both to exchange an authorization code and to refresh a token. */
    String getOAuthTokenUrl();

    /** OAuth scopes any one of which lets a token push over HTTP. */
    Set<String> getOAuthPushScopes();

    /**
     * Whether a token granted {@code reportedScopes}, as the provider reported them, can push. A token reported with no
     * scopes is taken to be able to: a GitHub App's user tokens carry none, their permissions being the app's.
     */
    default boolean grantsPush(String reportedScopes) {
        if (reportedScopes == null || reportedScopes.isBlank()) {
            return true;
        }
        return Arrays.stream(reportedScopes.split("[\\s,]+")).anyMatch(getOAuthPushScopes()::contains);
    }
}
