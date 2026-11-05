package com.rbc.fogwall.dashboard;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * An authentication established by {@link ApiKeyAuthFilter}. Its own type, rather than a username token with a
 * well-known principal name, so a caller can tell an API-key request from a dashboard user who happens to be named
 * after it.
 */
public final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    /** The principal name an API-key request acts as. */
    public static final String PRINCIPAL = "api-key";

    public ApiKeyAuthenticationToken(Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return PRINCIPAL;
    }
}
