package com.rbc.fogwall.dashboard.audit;

import com.rbc.fogwall.dashboard.ApiKeyAuthenticationToken;
import com.rbc.fogwall.permission.ChangeContext;
import com.rbc.fogwall.permission.ChangeOrigin;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The {@link ChangeContext} for an access change made by the current request: {@link ChangeOrigin#API} when it
 * authenticated with the API key, {@link ChangeOrigin#DASHBOARD} for a signed-in user.
 */
public final class AccessChangeContexts {

    private AccessChangeContexts() {}

    /** @throws IllegalStateException when the request is not authenticated — an access change always has an actor */
    public static ChangeContext current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("an access change requires an authenticated actor");
        }
        ChangeOrigin origin = auth instanceof ApiKeyAuthenticationToken ? ChangeOrigin.API : ChangeOrigin.DASHBOARD;
        return ChangeContext.by(origin, auth.getName());
    }
}
