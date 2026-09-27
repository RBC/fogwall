package com.rbc.fogwall.provider;

/**
 * Optional capability implemented by {@link FogwallProvider}s that SCM account linking supports: the provider issues
 * OAuth grants through the authorization-code flow and renews them through the refresh-token grant.
 *
 * <p>Both endpoints live on the provider's web host, which is not always the host its REST API is served from.
 */
public interface ScmOAuthProvider {

    /** The authorization endpoint the user is redirected to when linking an account. */
    String getOAuthAuthorizeUrl();

    /** The token endpoint, used both to exchange an authorization code and to refresh a grant. */
    String getOAuthTokenUrl();
}
