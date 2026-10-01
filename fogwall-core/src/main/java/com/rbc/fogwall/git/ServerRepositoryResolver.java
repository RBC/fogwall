package com.rbc.fogwall.git;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.filter.FogwallCredentialFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

/**
 * Repository resolver for server mode. Syncs a local bare repo from the upstream provider on each open, ensuring the
 * local mirror is fresh for both fetch and push operations.
 *
 * <p>Supports both public and private repositories. Client credentials are extracted from the request and used
 * transiently for the upstream clone/fetch — they are never written to disk. The same credentials are stored as a
 * request attribute ({@link #CREDENTIALS_ATTRIBUTE}) so that {@link ServerReceivePackFactory} can pass them to
 * {@link ForwardingPostReceiveHook} for the upstream push.
 */
@Slf4j
@RequiredArgsConstructor
public class ServerRepositoryResolver implements RepositoryResolver<HttpServletRequest> {

    public static final String CREDENTIALS_ATTRIBUTE = "com.rbc.fogwall.credentials";

    /** Upstream URL this request resolved to, read by {@link ServerReceivePackFactory} onto its PushContext. */
    public static final String UPSTREAM_URL_ATTRIBUTE = "com.rbc.fogwall.upstreamUrl";

    private final LocalRepositoryCache cache;
    private final FogwallProvider provider;

    @Override
    public Repository open(HttpServletRequest req, String name)
            throws RepositoryNotFoundException, ServiceMayNotContinueException {

        // JGit passes name as the path after the servlet mapping, e.g. "owner/repo.git"
        String cleanName = name.replaceAll("\\.git$", "");

        // Reject malformed segments before constructing the upstream URL — the servlet container's
        // URI normalization must not be the only defense against traversal in the repository path.
        for (String segment : cleanName.split("/")) {
            if (!RepoSlugValidator.isValidSegment(segment)) {
                log.warn("Rejecting server mode open with invalid repository path: {}", name);
                throw new RepositoryNotFoundException(name);
            }
        }

        // Construct the clean upstream URL (no credentials, ever)
        String cleanUpstreamUrl = provider.getUri() + "/" + cleanName + ".git";

        // Extract client credentials — used for both the upstream clone/fetch AND the upstream push.
        // Credentials are held in memory only and never written to disk.
        String[] userPass = extractCredentials(req);
        CredentialsProvider creds = null;
        // Identity for the mirror cache's per-principal fetch cooldown. Built from the full credential, not
        // the username: providers such as GitHub ignore the HTTP Basic username entirely, so the token is the
        // only part that actually identifies the caller. Hashed inside the cache; never retained raw.
        String principal = null;
        if (req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE)
                instanceof CredentialAuthentication authentication) {
            // FogwallCredentialFilter authenticated a fogwall credential and put the user's linked OAuth token in its
            // place; the credential itself never goes upstream.
            creds = (CredentialsProvider) req.getAttribute(CREDENTIALS_ATTRIBUTE);
            principal = "fogwall-user:" + authentication.user().getUsername();
        } else if (userPass != null) {
            creds = new UsernamePasswordCredentialsProvider(userPass[0], userPass[1]);
            principal = userPass[0] + ":" + userPass[1];
            req.setAttribute(CREDENTIALS_ATTRIBUTE, creds);
            req.setAttribute("com.rbc.fogwall.pushUser", userPass[0]);
        }

        log.debug("Opening server mode repository: {} -> {}", name, cleanUpstreamUrl);

        req.setAttribute(UPSTREAM_URL_ATTRIBUTE, cleanUpstreamUrl);

        try {
            return cache.getOrClone(cleanUpstreamUrl, creds, null, principal);
        } catch (Exception e) {
            UpstreamFailure failure = UpstreamFailure.classify(e);
            if (failure instanceof UpstreamFailure.Internal) {
                log.error("Failed to open repository {} from upstream {}", name, cleanUpstreamUrl, e);
            } else {
                log.warn("Upstream refused or failed {} ({}): {}", cleanUpstreamUrl, failure, e.getMessage());
            }
            throw new ServiceMayNotContinueException(message(failure), e, status(failure));
        }
    }

    /**
     * The status for a failed upstream open. A refusal of the credential's access follows the provider's configured
     * denial status, like every other refusal fogwall makes on discovery; the others say what happened.
     */
    private int status(UpstreamFailure failure) {
        return switch (failure) {
            case UpstreamFailure.CredentialRequired _, UpstreamFailure.NotAuthorized _ ->
                HttpServletResponse.SC_UNAUTHORIZED;
            case UpstreamFailure.Forbidden _ -> provider.getBlockedInfoRefsStatus();
            case UpstreamFailure.NotFound _ -> HttpServletResponse.SC_NOT_FOUND;
            case UpstreamFailure.Unavailable _ -> HttpServletResponse.SC_BAD_GATEWAY;
            case UpstreamFailure.Internal _ -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
    }

    /** An operator who answers refusals with 404 hides whether a repository exists, so a refusal reads as not found. */
    private String message(UpstreamFailure failure) {
        if (failure instanceof UpstreamFailure.Forbidden
                && provider.getBlockedInfoRefsStatus() == HttpServletResponse.SC_NOT_FOUND) {
            return new UpstreamFailure.NotFound().message();
        }
        return failure.message();
    }

    /**
     * Extracts the client's HTTP Basic credentials. These are reused for the upstream clone/fetch done by {@link #open}
     * above, and stored as a request attribute so {@link ForwardingPostReceiveHook} can reuse the same credentials for
     * the upstream push. They are held in memory only and never written to disk.
     *
     * <p>The {@code Authorization} header is the only source. Credentials a developer embeds in the remote URL
     * ({@code https://user:token@fogwall/...}) still arrive here, because git strips the userinfo out of the URL and
     * sends it as a Basic header once challenged — they never appear in the request line, and the servlet API excludes
     * userinfo from {@link HttpServletRequest#getRequestURL()} regardless.
     */
    private String[] extractCredentials(HttpServletRequest req) {
        // Try Authorization header first
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64 = authHeader.substring("Basic ".length()).trim();
                String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    return new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)};
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid Base64 in Authorization header", e);
            }
        }

        return null;
    }
}
