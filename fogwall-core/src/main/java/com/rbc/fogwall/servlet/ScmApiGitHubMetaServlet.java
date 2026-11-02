package com.rbc.fogwall.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers {@code GET /api/v3/meta}, the version handshake {@code gh} performs against any host other than
 * {@code github.com} before it chooses which query shapes to send.
 *
 * <p>The reply is static and carries no provider state, so it is served without authentication and never reaches the
 * upstream. The advertised version is the one {@code gh} treats as supporting the search and introspection queries the
 * GraphQL dialect forwards; it describes what this listener accepts, not the upstream's real release.
 */
public class ScmApiGitHubMetaServlet extends HttpServlet {

    /** Mount path of the handshake. */
    public static final String MOUNT = "/api/v3/meta";

    static final String ADVERTISED_VERSION = "3.18.0";

    private static final byte[] BODY =
            ("{\"installed_version\":\"" + ADVERTISED_VERSION + "\"}").getBytes(StandardCharsets.UTF_8);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getOutputStream().write(BODY);
    }
}
