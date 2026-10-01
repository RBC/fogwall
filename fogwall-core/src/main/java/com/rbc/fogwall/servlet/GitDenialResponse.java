package com.rbc.fogwall.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.http.server.GitSmartHttpTools;

/**
 * Writes a refusal a git client will display, and commits the response so nothing after the writer runs.
 *
 * <p>git shows a server's message in only two shapes. On the {@code /info/refs} discovery request, a {@code text/plain}
 * body on an error status: git prints each line as {@code remote: <line>}, then its own line for the status
 * ({@code repository not found} for 404, {@code Authentication failed} for 401, the bare status otherwise). On the
 * {@code git-upload-pack} and {@code git-receive-pack} requests that follow, only a git-protocol {@code ERR} packet on
 * a 200, printed as {@code fatal: remote error: <message>}. Any other shape, including JGit's own {@code ERR} packet on
 * an error status and a servlet container's HTML error page, shows the status alone.
 */
public final class GitDenialResponse {

    private static final String TEXT_PLAIN = "text/plain; charset=UTF-8";

    private GitDenialResponse() {}

    /**
     * Refuses {@code request} with {@code status} and {@code message}. The status reaches the client on discovery; on
     * the requests after it, git displays a message only on a 200, so the status is not sent there.
     */
    public static void send(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws IOException {
        if (GitSmartHttpTools.isInfoRefs(request)) {
            writeText(response, status, message);
        } else if (GitSmartHttpTools.isUploadPack(request) || GitSmartHttpTools.isReceivePack(request)) {
            GitSmartHttpTools.sendError(request, response, HttpServletResponse.SC_OK, message);
        } else {
            response.sendError(status, message);
        }
    }

    /**
     * Writes {@code message} as a {@code text/plain} body on {@code status} and commits the response. A 401 carries a
     * Basic challenge when none is set yet, so git drops the credential it sent and asks for another.
     */
    public static void writeText(HttpServletResponse response, int status, String message) throws IOException {
        byte[] body = (message.strip() + "\n").getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        if (status == HttpServletResponse.SC_UNAUTHORIZED && response.getHeader("WWW-Authenticate") == null) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
        }
        response.setContentType(TEXT_PLAIN);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.flushBuffer();
    }
}
