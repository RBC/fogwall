package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.servlet.GitDenialResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.transport.PacketLineIn;

/**
 * Turns server-mode git errors into responses the git client displays. JGit's {@code GitSmartHttpTools.sendError()}
 * writes the message as a git-protocol {@code ERR} packet on the error status, and git never reads a body that arrives
 * on an error status in that form: the developer sees {@code The requested URL returned error: 403} and nothing else.
 * See {@link GitDenialResponse} for the two shapes git does display.
 *
 * <ul>
 *   <li>On {@code /info/refs} discovery, an error keeps its status and its message is rewritten as a {@code text/plain}
 *       body.
 *   <li>On {@code git-upload-pack} and {@code git-receive-pack}, an error status becomes 200 so the {@code ERR} packet
 *       is read.
 *   <li>On either, {@code sendError} writes the message and commits the response, so the request ends there. A filter
 *       that refuses by calling it must not find the request carrying on into the GitServlet.
 * </ul>
 *
 * <p>401 always passes through with its status: git needs it to ask for a credential.
 */
public class SmartHttpErrorFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpReq = (HttpServletRequest) request;
        var httpResp = (HttpServletResponse) response;

        if (GitSmartHttpTools.isInfoRefs(httpReq)) {
            chain.doFilter(request, new InfoRefsErrorResponseWrapper(httpResp));
            return;
        }
        if (!GitSmartHttpTools.isUploadPack(httpReq) && !GitSmartHttpTools.isReceivePack(httpReq)) {
            chain.doFilter(request, response);
            return;
        }

        // For receive-pack, use a tiny response buffer so sideband messages (remote: ...)
        // stream to the git client in real time instead of being batched at the end.
        if (GitSmartHttpTools.isReceivePack(httpReq)) {
            httpResp.setBufferSize(256);
        }

        chain.doFilter(request, new ForceOkOnErrorResponseWrapper(httpReq, httpResp));
    }

    /**
     * Keeps the status of a discovery error and rewrites JGit's {@code ERR} packet body into the {@code text/plain} one
     * git prints. A body that is already {@code text/plain} passes through untouched.
     */
    private static final class InfoRefsErrorResponseWrapper extends HttpServletResponseWrapper {

        private int status = SC_OK;
        private boolean rewriting;
        private ServletOutputStream rewritingStream;

        InfoRefsErrorResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            status = sc;
            super.setStatus(sc);
        }

        @Override
        public void setContentType(String type) {
            if (status >= 400 && type != null && type.startsWith("application/x-git-")) {
                rewriting = true;
                return;
            }
            super.setContentType(type);
        }

        @Override
        public void setContentLength(int len) {
            if (!rewriting) super.setContentLength(len);
        }

        @Override
        public void setContentLengthLong(long len) {
            if (!rewriting) super.setContentLengthLong(len);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!rewriting) {
                return super.getOutputStream();
            }
            if (rewritingStream == null) {
                rewritingStream = new ErrPacketRewritingStream((HttpServletResponse) getResponse(), status);
            }
            return rewritingStream;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            GitDenialResponse.writeText((HttpServletResponse) getResponse(), sc, msg != null ? msg : "HTTP " + sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (sc == SC_UNAUTHORIZED) {
                // A bare challenge: the caller has set the header git needs, and there is nothing to say.
                super.sendError(sc);
                return;
            }
            sendError(sc, null);
        }
    }

    /** Collects JGit's {@code ERR} packet and, on close, writes its text as the {@code text/plain} body. */
    private static final class ErrPacketRewritingStream extends ServletOutputStream {

        private final HttpServletResponse response;
        private final int status;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean closed;

        ErrPacketRewritingStream(HttpServletResponse response, int status) {
            this.response = response;
            this.status = status;
        }

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            GitDenialResponse.writeText(response, status, errText(buffer.toByteArray(), status));
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException("synchronous only");
        }

        /** The text of the first {@code ERR} packet, past the service announcement and flush JGit writes before it. */
        private static String errText(byte[] body, int status) {
            PacketLineIn in = new PacketLineIn(new ByteArrayInputStream(body));
            try {
                for (String line = in.readString(); ; line = in.readString()) {
                    if (!PacketLineIn.isEnd(line) && line.startsWith("ERR ")) {
                        return line.substring("ERR ".length());
                    }
                }
            } catch (IOException endOfBody) {
                return "HTTP " + status;
            }
        }
    }

    /**
     * Replaces 4xx/5xx statuses with 200, except 401, which must reach the client so git sends credentials. JGit writes
     * the error message as an {@code ERR} packet, and git reads it only on a 200.
     */
    private static final class ForceOkOnErrorResponseWrapper extends HttpServletResponseWrapper {

        private final HttpServletRequest request;

        ForceOkOnErrorResponseWrapper(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc >= 400 && sc != SC_UNAUTHORIZED ? SC_OK : sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (sc == SC_UNAUTHORIZED) {
                super.sendError(sc, msg);
                return;
            }
            GitSmartHttpTools.sendError(
                    request, (HttpServletResponse) getResponse(), SC_OK, msg != null ? msg : "HTTP " + sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (sc == SC_UNAUTHORIZED) {
                super.sendError(sc);
                return;
            }
            sendError(sc, null);
        }
    }
}
