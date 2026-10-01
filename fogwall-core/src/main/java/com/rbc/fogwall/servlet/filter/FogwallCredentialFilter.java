package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.CredentialAuthentication;
import com.rbc.fogwall.git.ScmOAuthCredentialsProvider;
import com.rbc.fogwall.git.ServerRepositoryResolver;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ScmOAuthProvider;
import com.rbc.fogwall.service.GitCredentialService;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.service.ScmOAuthTokenService.Access;
import com.rbc.fogwall.servlet.FetchDecision;
import com.rbc.fogwall.servlet.GitDenialResponse;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.http.server.GitSmartHttpTools;

/**
 * Authenticates server-mode git requests that carry a fogwall-issued credential, and forwards them upstream with the
 * user's linked OAuth token instead of the credential.
 *
 * <p>A request whose password is not a fogwall credential passes through untouched: the client's own SCM credential is
 * used upstream, as without this filter. A fogwall credential is never sent upstream. It is refused outright on a
 * provider that does not broker pushes, rejected with a fresh challenge when it is invalid, expired or revoked, and
 * refused with the remedy when the user's linked OAuth token cannot be used, or, for a push, was granted no scope that
 * can push.
 *
 * <p>On success the request carries the {@link CredentialAuthentication}, which fixes the pushing user, and a
 * {@link ScmOAuthCredentialsProvider} in place of the client's credential for the mirror sync and the forward.
 */
@Slf4j
public class FogwallCredentialFilter implements Filter {

    /** Request attribute holding the {@link CredentialAuthentication} of a request this filter authenticated. */
    public static final String AUTHENTICATION_ATTRIBUTE = "com.rbc.fogwall.credentialAuthentication";

    private final FogwallProvider provider;
    private final boolean brokeredPush;
    private final GitCredentialService credentials;
    private final ScmOAuthTokenService oauthTokens;
    private final ReadOnlyUserStore users;
    private final String serviceUrl;

    /**
     * @param brokeredPush whether this provider forwards pushes with linked OAuth tokens; when false, fogwall
     *     credentials are refused on it
     */
    public FogwallCredentialFilter(
            FogwallProvider provider,
            boolean brokeredPush,
            GitCredentialService credentials,
            ScmOAuthTokenService oauthTokens,
            ReadOnlyUserStore users,
            String serviceUrl) {
        this.provider = provider;
        this.brokeredPush = brokeredPush;
        this.credentials = credentials;
        this.oauthTokens = oauthTokens;
        this.users = users;
        this.serviceUrl = serviceUrl;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var resp = (HttpServletResponse) response;

        String password = basicPassword(req);
        if (!GitCredentialService.isFogwallCredential(password)) {
            chain.doFilter(request, response);
            return;
        }

        if (!brokeredPush) {
            log.warn(
                    "Refused a fogwall credential for provider '{}', which does not broker pushes", provider.getName());
            refuseFetch(req, FetchRefusal.CREDENTIAL_REFUSED);
            GitDenialResponse.send(
                    req,
                    resp,
                    provider.getBlockedInfoRefsStatus(),
                    "fogwall credentials are not accepted for " + provider.getName() + ". Use your "
                            + provider.getName() + " credential for this remote.");
            return;
        }

        Optional<GitCredential> credential = credentials.authenticate(password);
        Optional<UserEntry> user = credential.flatMap(c -> users.findByUsername(c.username()));
        if (user.isEmpty()) {
            refuseFetch(req, FetchRefusal.CREDENTIAL_REFUSED);
            // A fresh challenge, so git discards the stored credential and asks for another.
            resp.setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String username = user.get().getUsername();
        Access access = oauthTokens.access(username, provider.getName());
        if (access instanceof Access.Unusable unusable) {
            log.info(
                    "Refused request from user '{}': linked {} OAuth token unusable ({})",
                    username,
                    provider.getName(),
                    unusable.reason());
            refuseFetch(req, FetchRefusal.LINKED_TOKEN_UNUSABLE);
            GitDenialResponse.send(
                    req, resp, provider.getBlockedInfoRefsStatus(), unusableTokenMessage(unusable.reason()));
            return;
        }
        if (access instanceof Access.Usable usable && isPush(req) && !grantsPush(usable.scopes())) {
            // Linked before this provider brokered pushes, so only for reading. Refused here rather than at forward,
            // after the push has run every check and perhaps waited for a review.
            log.info(
                    "Refused push from user '{}': linked {} OAuth token lacks push scope (has '{}')",
                    username,
                    provider.getName(),
                    usable.scopes());
            GitDenialResponse.send(req, resp, provider.getBlockedInfoRefsStatus(), missingPushScopeMessage());
            return;
        }

        req.setAttribute(
                AUTHENTICATION_ATTRIBUTE,
                new CredentialAuthentication(
                        user.get(), credential.get().id(), credential.get().name()));
        req.setAttribute(
                ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE,
                new ScmOAuthCredentialsProvider(oauthTokens, username, provider.getName()));
        chain.doFilter(request, response);
    }

    /** Counts the refusal when it ends a clone or fetch. A push refusal is the push record's to keep. */
    private static void refuseFetch(HttpServletRequest req, FetchRefusal refusal) {
        if (!isPush(req)) {
            FetchDecision.blocked(req, refusal, null);
        }
    }

    /** What a user whose linked OAuth token cannot be used is told, for both fetch and push. */
    String unusableTokenMessage(ScmOAuthTokenService.Reason reason) {
        String name = provider.getName();
        String profile = serviceUrl != null ? serviceUrl + "/dashboard/profile" : "your fogwall profile";
        return switch (reason) {
            case NOT_LINKED -> "Your " + name + " account is not linked to fogwall. Link it at " + profile + ".";
            case EXPIRED -> "Your linked " + name + " account can no longer be used. Link it again at " + profile + ".";
            case REFRESH_FAILED -> "fogwall could not renew access to your linked " + name + " account. Retry shortly.";
            case KEY_UNAVAILABLE -> "fogwall cannot use linked accounts right now. Contact an administrator.";
            case LINK_EXPIRED ->
                "Your " + name + " account was linked too long ago to be used. Link it again at " + profile + ".";
        };
    }

    String missingPushScopeMessage() {
        String profile = serviceUrl != null ? serviceUrl + "/dashboard/profile" : "your fogwall profile";
        return "Your " + provider.getName() + " account was linked without permission to push. Link it again at "
                + profile + ".";
    }

    private boolean grantsPush(String scopes) {
        return !(provider instanceof ScmOAuthProvider oauth) || oauth.grantsPush(scopes);
    }

    /** Whether the request is part of a push: its ref advertisement, or the pack upload. */
    private static boolean isPush(HttpServletRequest req) {
        if (GitSmartHttpTools.isReceivePack(req)) {
            return true;
        }
        return GitSmartHttpTools.isInfoRefs(req) && "git-receive-pack".equals(req.getParameter("service"));
    }

    private static String basicPassword(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder()
                            .decode(header.substring("Basic ".length()).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon >= 0 ? decoded.substring(colon + 1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
