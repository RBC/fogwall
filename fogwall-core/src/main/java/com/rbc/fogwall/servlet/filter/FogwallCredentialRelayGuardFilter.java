package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.service.GitCredentialService;
import com.rbc.fogwall.servlet.GitDenialResponse;
import com.rbc.fogwall.servlet.ScmApiTokenExtractor;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Refuses transparent-proxy requests that carry a fogwall-issued credential, which the proxy would otherwise relay
 * upstream verbatim. A credential helper that stores one under the fogwall host, without keying on the path, offers it
 * to every remote on that host, including {@code /proxy/} ones.
 *
 * <p>Costs a header read per request, and a short base64 decode when the header is HTTP Basic.
 */
@Slf4j
public class FogwallCredentialRelayGuardFilter implements Filter {

    static final String MESSAGE = "fogwall credentials work only with server-mode remotes (/server/...), and this one"
            + " was not sent upstream. Use your own SCM credential for this remote.";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        if (carriesFogwallCredential(req)) {
            log.warn("Refused a fogwall credential on transparent-proxy path {}", req.getRequestURI());
            GitDenialResponse.send(req, (HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN, MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Whether any credential header on {@code request} carries a fogwall-issued credential: as either half of HTTP
     * Basic, or as the token of any other scheme or of GitLab's {@code PRIVATE-TOKEN}.
     */
    public static boolean carriesFogwallCredential(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Basic ")) {
            String decoded =
                    decodeBasic(authorization.substring("Basic ".length()).trim());
            int colon = decoded.indexOf(':');
            String user = colon >= 0 ? decoded.substring(0, colon) : decoded;
            String password = colon >= 0 ? decoded.substring(colon + 1) : "";
            return GitCredentialService.isFogwallCredential(user) || GitCredentialService.isFogwallCredential(password);
        }
        return GitCredentialService.isFogwallCredential(ScmApiTokenExtractor.extractToken(request));
    }

    private static String decodeBasic(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
