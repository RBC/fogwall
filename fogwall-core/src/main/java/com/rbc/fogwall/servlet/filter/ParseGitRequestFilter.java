package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.RED;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.CROSS_MARK;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.NO_ENTRY;
import static com.rbc.fogwall.git.GitClientUtils.sym;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitClientUtils;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.RepoSlugValidator;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.PushTooLargeException;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PacketLineIn;

/**
 * Filter that extracts details about a git request and adds them to the request attributes. This filter is used to
 * extract the details so that they can be used by other filters for processing. This filter runs after the default
 * {@link ForceGitClientFilter}.
 */
@Slf4j
public class ParseGitRequestFilter extends ProviderAwareFogwallFilter<FogwallProvider> {

    private static final int ORDER = Integer.MIN_VALUE + 1;

    private final long maxPushBytes;

    public ParseGitRequestFilter(FogwallProvider provider) {
        this(provider, 0);
    }

    /** @param maxPushBytes largest request body to accept, in bytes; 0 disables the check */
    public ParseGitRequestFilter(FogwallProvider provider, long maxPushBytes) {
        super(ORDER, provider);
        this.maxPushBytes = maxPushBytes;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Cheap pre-check: reject a declared over-size body without reading it at all. Clients using chunked
        // encoding declare no length, so this is an optimisation and the wrapper's counting read is the
        // actual bound.
        long declared = httpRequest.getContentLengthLong();
        if (maxPushBytes > 0 && declared > maxPushBytes) {
            sendTooLarge(httpRequest, (HttpServletResponse) response, maxPushBytes, declared);
            return;
        }

        // Create the wrapper to capture the body
        RequestBodyWrapper wrapper;
        try {
            wrapper = new RequestBodyWrapper(httpRequest, maxPushBytes);
        } catch (PushTooLargeException e) {
            sendTooLarge(httpRequest, (HttpServletResponse) response, e.getLimitBytes(), -1);
            return;
        }

        // Parse the git request details
        GitRequestDetails requestDetails = parse(wrapper);

        // Add the details to the request attributes
        wrapper.setAttribute(GIT_REQUEST_ATTR, requestDetails);

        if (System.getenv().containsKey("fogwall_DEBUG_CLIENT")
                && !System.getenv("fogwall_DEBUG_CLIENT").equals("")) {
            log.info("remote addr: {}", request.getRemoteAddr());
            log.info("user-agent: {}", ((HttpServletRequest) request).getHeader("User-Agent"));
        }

        // Block rejected requests (multi-ref pushes, invalid repository paths) immediately — do not
        // let them reach downstream filters
        if (requestDetails.getResult() == GitRequestDetails.GitResult.REJECTED) {
            String titleText =
                    requestDetails.getRejectionTitle() != null ? requestDetails.getRejectionTitle() : "Request Blocked";
            String title = sym(NO_ENTRY) + "  " + titleText;
            String message = sym(CROSS_MARK) + "  " + requestDetails.getReason();
            sendGitError(wrapper, (HttpServletResponse) response, GitClientUtils.format(title, message, RED, null));
            return;
        }

        // Continue with the wrapped request (important!)
        chain.doFilter(wrapper, response);
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // no-op
    }

    /**
     * Reports an over-size body to the git client.
     *
     * <p>Sent via {@code sendGitError} rather than an HTTP 413 because git only surfaces protocol-level errors to the
     * user; a bare status code produces an opaque failure. {@code declared} is the client's {@code Content-Length}, or
     * -1 when the limit was hit mid-read and the true size is unknown.
     */
    private void sendTooLarge(HttpServletRequest request, HttpServletResponse response, long limitBytes, long declared)
            throws IOException {
        log.warn(
                "Rejecting request body over the {}-byte limit (declared {}): {}",
                limitBytes,
                declared >= 0 ? declared : "unknown, chunked",
                request.getRequestURI());
        String title = sym(NO_ENTRY) + "  Push Blocked - Too Large";
        String sizeLine = declared >= 0
                ? sym(CROSS_MARK) + "  This push is " + humanReadable(declared) + "; the limit is "
                        + humanReadable(limitBytes) + "."
                : sym(CROSS_MARK) + "  This push exceeds the " + humanReadable(limitBytes) + " limit.";
        String message = sizeLine + "\n\n"
                + "Large files usually mean binaries or generated output that don't belong in git history.\n"
                + "If the content is genuinely needed, contact an administrator — a one-off import is normally\n"
                + "seeded directly upstream rather than pushed through the proxy.";
        sendGitError(request, response, GitClientUtils.format(title, message, RED, null));
    }

    private static String humanReadable(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format("%.1f GiB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024L) return String.format("%.0f MiB", bytes / (1024.0 * 1024));
        return bytes + " bytes";
    }

    /**
     * Parse the {@link GitRequestDetails} details from the request body.
     *
     * @param request The HTTP request
     * @return The parsed push request
     */
    public GitRequestDetails parse(RequestBodyWrapper request) {
        var gr = new GitRequestDetails();
        gr.setProvider(provider);
        gr.getFilters().add(this);
        var op = determineOperation(request);
        gr.setOperation(op);
        gr.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner(getOwner(request.getPathInfo()))
                .name(getName(request.getPathInfo()))
                .slug(getSlug(request.getPathInfo()))
                .build());
        // Reject malformed owner/name before they reach URL rules, permission checks, or upstream URL
        // construction — the servlet container's URI normalization must not be the only defense.
        if (!RepoSlugValidator.isValidSegment(gr.getRepoRef().getOwner())
                || !RepoSlugValidator.isValidSegment(gr.getRepoRef().getName())) {
            log.warn("Rejecting request with invalid repository path: {}", request.getPathInfo());
            gr.setResult(GitRequestDetails.GitResult.REJECTED);
            gr.setRejectionTitle("Request Blocked - Invalid Repository Path");
            gr.setReason("Repository owner and name may only contain letters, digits, '.', '_' and '-'.");
            return gr;
        }
        if (op == HttpOperation.INFO) {
            gr.setResult(GitRequestDetails.GitResult.ALLOWED);
        }
        if (op == HttpOperation.PUSH) {
            try {
                // Read packet line using JGit
                var pli = new PacketLineIn(request.getInputStream());
                String packetLine = pli.readStringRaw();

                // Skip shallow pkt-lines sent by shallow-clone clients before the ref update
                while (packetLine.startsWith("shallow ")) {
                    packetLine = pli.readStringRaw();
                }

                // CVE-2025-54583: Reject multi-ref pushes. Read the next pkt-line — it must
                // be a flush packet (0000). If it's another ref update, the client is pushing
                // multiple branches and we must reject.
                String nextLine = pli.readString();
                if (!PacketLineIn.isEnd(nextLine)) {
                    log.warn("Multi-ref push detected — rejecting. First ref: {}", packetLine.trim());
                    gr.setResult(GitRequestDetails.GitResult.REJECTED);
                    gr.setRejectionTitle("Push Blocked - Multi-Branch Push");
                    gr.setReason("Multi-branch pushes are not allowed. Please push one branch at a time.");
                    return gr;
                }

                // Parse old SHA, new SHA, ref from the pkt-line (space-separated; ref may have
                // capability strings after a NUL byte which we strip).
                String[] parts = packetLine.split(" ");
                gr.setCommitFrom(parts[0]);
                gr.setCommitTo(parts[1]);
                // capability strings appear after a NUL byte in parts[2]
                gr.setBranch(parts[2].split("\0")[0].trim());
            } catch (IOException e) {
                log.error("Error parsing push request", e);
            }
        }
        return gr;
    }

    private static String getOwner(String pathInfo) {
        var parts = pathInfo.split("/");
        return parts.length < 3 ? pathInfo : parts[1];
    }

    private static String getName(String pathInfo) {
        var parts = pathInfo.split("/");
        return parts.length < 3 ? pathInfo : parts[2].replace(Constants.DOT_GIT_EXT, "");
    }

    private static String getSlug(String pathInfo) {
        var parts = pathInfo.split("/");
        if (parts.length < 3) return pathInfo;
        return "/" + String.join("/", parts[1], parts[2]).replace(Constants.DOT_GIT_EXT, "");
    }
}
