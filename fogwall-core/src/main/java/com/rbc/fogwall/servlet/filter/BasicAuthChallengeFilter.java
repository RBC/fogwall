package com.rbc.fogwall.servlet.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jgit.http.server.GitSmartHttpTools;

/**
 * Servlet filter that challenges unauthenticated requests with HTTP 401 and {@code WWW-Authenticate: Basic}. Git
 * clients only send credentials after receiving a 401 challenge - without this, credentials embedded in the remote URL
 * (e.g. {@code http://user:token@proxy/...}) are never transmitted.
 *
 * <p>Challenges both receive-pack (push) and upload-pack (fetch/clone) requests. Store-and-forward clones the upstream
 * repo on every open — including fetches — so a private repo must be able to receive credentials on the fetch path too,
 * not just push; without a challenge here, a git client with no credentials embedded in the URL never learns it needs
 * to send any, and the anonymous upstream clone/fetch fails. Git clients that already hold credentials (a credential
 * helper, or userinfo embedded in the URL) resend the request with an {@code Authorization} header transparently, so
 * genuinely public repos are unaffected as long as the client has *some* credential to offer.
 *
 * <p>Matches both the {@code info/refs} advertisement and the actual {@code POST /git-upload-pack} or {@code POST
 * /git-receive-pack} data exchange.
 */
public class BasicAuthChallengeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpReq = (HttpServletRequest) request;
        var httpResp = (HttpServletResponse) response;

        if (isGitSmartHttpRequest(httpReq)) {
            String auth = httpReq.getHeader("Authorization");
            if (auth == null || auth.isBlank()) {
                httpResp.setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
                httpResp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isGitSmartHttpRequest(HttpServletRequest req) {
        return GitSmartHttpTools.isReceivePack(req)
                || GitSmartHttpTools.isUploadPack(req)
                || GitSmartHttpTools.isInfoRefs(req);
    }
}
