package com.rbc.fogwall.servlet;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Terminal handler for every request on an SCM API listener that no dialect mount matched. Answers with an explanation
 * of what the listener covers rather than the container's empty 404, which reads as fogwall being broken.
 *
 * <p>Sits behind the audit, authentication and user-agent filters, so a refusal of an authenticated caller is recorded
 * like any other denial and an anonymous probe is turned away before it gets this far. The message states the boundary
 * only; it does not suggest going to the provider directly, since on some deployments fogwall is the only route.
 */
public class ScmApiOutOfScopeServlet extends HttpServlet {

    private static final int MAX_PATH_ECHO = 120;

    private final String providerName;

    public ScmApiOutOfScopeServlet(String providerName) {
        this.providerName = providerName;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var context = (ScmApiRequestContext) request.getAttribute(SCM_API_REQUEST_ATTR);
        String target = request.getMethod() + " " + abbreviate(request.getRequestURI());
        ScmApiGateResponse.deny(
                context,
                response,
                HttpServletResponse.SC_NOT_FOUND,
                "'" + target + "' is outside the " + providerName
                        + " SCM API. fogwall serves issues, pull and merge requests, and the reads the CLIs perform to"
                        + " drive them; other endpoints are not available here.");
    }

    private static String abbreviate(String path) {
        return path == null || path.length() <= MAX_PATH_ECHO ? path : path.substring(0, MAX_PATH_ECHO) + "…";
    }
}
