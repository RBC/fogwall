package com.rbc.fogwall.dashboard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Redirects the pre-rename OAuth2 callback path to the current registration ID.
 *
 * <p>The project rename changed the Spring Security registration ID from {@code gitproxy} to {@code fogwall}, which
 * changes the callback URL that must be registered in the identity provider (Entra ID, Okta, etc.). This filter issues
 * a 302 redirect so existing IdP app registrations keep working without reconfiguration.
 *
 * <p>Remove in 1.3.0 once all operator deployments have updated their IdP redirect URIs.
 */
class LegacyOidcCallbackRedirectFilter extends OncePerRequestFilter {

    static final String LEGACY_PATH = "/login/oauth2/code/gitproxy";
    static final String CURRENT_PATH = "/login/oauth2/code/fogwall";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (LEGACY_PATH.equals(request.getServletPath())) {
            String qs = request.getQueryString();
            response.sendRedirect(CURRENT_PATH + (qs != null ? "?" + qs : ""));
            return;
        }
        chain.doFilter(request, response);
    }
}
